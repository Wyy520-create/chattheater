package com.chattheater.theater.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 推送中心：按剧场（groupId）分房间广播。
 *
 * 协议事件：hello | typing | chunk | message | system | heat | ended | ping(心跳15s)。
 * EventSource 不能自定义 Header → 认证走 ?token= 查询参数（AuthInterceptor 兼容）。
 *
 * 设计要点：
 * - 每个房间 CopyOnWriteArrayList，广播时对失效连接惰性清除（读多写少场景）；
 * - 心跳用单线程全局调度器，ping 失败即移除该连接；
 * - 发送异常只移除连接不抛出——推送失败绝不能影响聊天引擎主循环。
 */
@Slf4j
@Service
public class SseHub {

    /** 心跳间隔（毫秒）。 */
    private static final long HEARTBEAT_MS = 15_000;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private final ObjectMapper mapper = new ObjectMapper();

    public SseHub() {
        heartbeat.scheduleAtFixedRate(this::pingAll, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
    }

    /** 观众进入直播间。 */
    public SseEmitter join(Long groupId) {
        SseEmitter emitter = new SseEmitter(0L);
        rooms.computeIfAbsent(groupId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> leave(groupId, emitter));
        emitter.onTimeout(() -> leave(groupId, emitter));
        emitter.onError(e -> leave(groupId, emitter));
        send(emitter, "hello", Map.of("groupId", groupId, "heartbeat", HEARTBEAT_MS));
        log.debug("[SSE] 剧场 {} 新观众，当前在线 {}", groupId, rooms.getOrDefault(groupId, new CopyOnWriteArrayList<>()).size());
        return emitter;
    }

    public void leave(Long groupId, SseEmitter emitter) {
        List<SseEmitter> room = rooms.get(groupId);
        if (room != null) {
            room.remove(emitter);
        }
    }

    /** 演员 X 正在打字（前端展示临时气泡）。 */
    public void typing(Long groupId, Long personaId, String name, String avatar) {
        broadcast(groupId, "typing", Map.of("pid", personaId, "name", name, "avatar", avatar));
    }

    /** 流式增量（前端往临时气泡追加文字）。 */
    public void chunk(Long groupId, Long personaId, String delta) {
        broadcast(groupId, "chunk", Map.of("pid", personaId, "delta", delta));
    }

    /** 一条正式消息定稿（前端用 pid 替换临时气泡）。 */
    public void message(Long groupId, Object messageVo) {
        broadcast(groupId, "message", messageVo);
    }

    /** 系统气泡（降级提示/导演操作回显等）。 */
    public void system(Long groupId, String text) {
        broadcast(groupId, "system", Map.of("text", text));
    }

    /** 热度变化。 */
    public void heat(Long groupId, int heat) {
        broadcast(groupId, "heat", Map.of("heat", heat));
    }

    /** 投票卡实时状态（发起/投票/结算都推全量 PollVO）。 */
    public void poll(Long groupId, Object data) {
        broadcast(groupId, "poll", data);
    }

    /** 剧目完结谢幕。 */
    public void ended(Long groupId) {
        broadcast(groupId, "ended", Map.of("groupId", groupId));
    }

    /** 某剧场当前在线观众数（房间缺席时为 0）。 */
    public int audience(Long groupId) {
        List<SseEmitter> room = rooms.get(groupId);
        return room == null ? 0 : room.size();
    }

    /** 全平台在线观众总数（广场统计用）。 */
    public int totalAudience() {
        return rooms.values().stream().mapToInt(List::size).sum();
    }

    private void broadcast(Long groupId, String event, Object data) {
        List<SseEmitter> room = rooms.get(groupId);
        if (room == null || room.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : room) {
            if (!send(emitter, event, data)) {
                leave(groupId, emitter);
            }
        }
    }

    /** 单连接发送：返回 false 表示连接已死，调用方负责移除。 */
    private boolean send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(mapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    private void pingAll() {
        rooms.forEach((groupId, room) -> {
            for (SseEmitter emitter : room) {
                if (!send(emitter, "ping", Map.of("ts", System.currentTimeMillis()))) {
                    leave(groupId, emitter);
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
        rooms.values().forEach(room -> room.forEach(SseEmitter::complete));
        rooms.clear();
    }
}
