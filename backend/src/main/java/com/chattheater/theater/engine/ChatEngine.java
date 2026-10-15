package com.chattheater.theater.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chattheater.ai.ChatContext;
import com.chattheater.ai.EmotionUtil;
import com.chattheater.ai.LlmGateway;
import com.chattheater.moderation.ModerationService;
import com.chattheater.persona.entity.Persona;
import com.chattheater.persona.mapper.PersonaMapper;
import com.chattheater.theater.entity.GroupMember;
import com.chattheater.theater.entity.Message;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.mapper.GroupMemberMapper;
import com.chattheater.theater.mapper.MessageMapper;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.theater.sse.SseHub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天引擎（本项目心脏）：每剧场一个 GroupRuntime + 单线程 executor。
 *
 * 事件循环（由用户消息/导演指令/事件卡/恢复演出触发）：
 * 1. looping CAS 抢锁防重入；一轮最多连发 8 条，防"AI 自嗨"。
 * 2. 加权选人：候选=未禁言成员，排除上一发言者（除非只剩一人），权重=活跃度，
 *    10% 概率完全随机（戏剧性）。
 * 3. 上下文：人设 system + 舞台快照 + 导演总指令 + 最近 24 条记录 + 待注入指令。
 * 4. 流式生成：网关 onDelta → SSE chunk；完成落库。
 * 5. 连发判定：首轮必回；此后 p = 0.35 + 平均活跃度×0.4 + 热度×0.15（上限 0.9）；
 *    间隔 500~1700ms 模拟真人节奏。
 * 6. 副作用：message_count/heat 原子自增、情绪启发式标注、SSE 广播、llm_usage 计量。
 *
 * 并发模型：所有 DB 写与 SSE 广播串行化于单线程 executor，跨剧场天然并行；
 * 单实例假设（多实例需外置 runtime，已列入设计边界）。
 */
@Slf4j
@Component
public class ChatEngine {

    private static final int MAX_BURST = 8;
    private static final int HISTORY_WINDOW = 24;
    private static final long SPEAK_GAP_MIN_MS = 500;
    private static final long SPEAK_GAP_MAX_MS = 1_700;

    /** 剧组成员的内存态（人设运行期不变，activity/muted 可被导演实时调整）。 */
    static final class MemberState {
        final Persona persona;
        volatile int activity;
        volatile boolean muted;

        MemberState(Persona persona, int activity, boolean muted) {
            this.persona = persona;
            this.activity = activity;
            this.muted = muted;
        }
    }

    /** 每剧场一份运行时状态。 */
    static final class GroupRuntime {
        final Long groupId;
        final ExecutorService executor;
        final AtomicBoolean looping = new AtomicBoolean(false);
        volatile boolean paused;
        volatile boolean ended;
        volatile Long lastSpeakerId;
        /** 已提示过 Mock 降级（一次降级只提示一次，避免刷屏）。 */
        volatile boolean degradedNotified;
        final Map<Long, MemberState> members = new ConcurrentHashMap<>();
        final ConcurrentLinkedDeque<String> pendingDirectives = new ConcurrentLinkedDeque<>();

        GroupRuntime(Long groupId) {
            this.groupId = groupId;
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "theater-engine-" + groupId);
                t.setDaemon(true);
                return t;
            });
        }
    }

    private final Map<Long, GroupRuntime> runtimes = new ConcurrentHashMap<>();

    private final TheaterGroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final MessageMapper messageMapper;
    private final PersonaMapper personaMapper;
    private final LlmGateway gateway;
    private final SseHub sseHub;
    private final ModerationService moderation;

    public ChatEngine(TheaterGroupMapper groupMapper, GroupMemberMapper memberMapper,
                      MessageMapper messageMapper, PersonaMapper personaMapper,
                      LlmGateway gateway, SseHub sseHub, ModerationService moderation) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.personaMapper = personaMapper;
        this.gateway = gateway;
        this.sseHub = sseHub;
        this.moderation = moderation;
    }

    // ==================== 对外触发入口（供 TheaterService 调用） ====================

    /** 触发一轮 AI 自动对话（幂等：循环进行中则忽略，新消息会被下一轮上下文读到）。 */
    public void ignite(Long groupId) {
        GroupRuntime rt = runtime(groupId);
        if (rt == null || rt.paused || rt.ended) {
            return;
        }
        if (!rt.looping.compareAndSet(false, true)) {
            return;
        }
        try {
            rt.executor.submit(() -> runLoop(rt));
        } catch (RejectedExecutionException e) {
            rt.looping.set(false);
        }
    }

    /** 注入导演指令/事件卡，并尝试触发一轮。 */
    public void pushDirective(Long groupId, String text) {
        GroupRuntime rt = runtime(groupId);
        if (rt == null) {
            return;
        }
        rt.pendingDirectives.offer(text);
        ignite(groupId);
    }

    /** 喊卡：AI 静默（用户消息仍可发）。 */
    public void pause(Long groupId) {
        GroupRuntime rt = runtimes.get(groupId);
        if (rt != null) {
            rt.paused = true;
        }
    }

    /** 恢复演出。 */
    public void resume(Long groupId) {
        GroupRuntime rt = runtime(groupId);
        if (rt != null) {
            rt.paused = false;
            ignite(groupId);
        }
    }

    /** 导演调整了活跃度/禁言：把 DB 最新状态刷进内存（人设集合运行期不变）。 */
    public void syncMembers(Long groupId) {
        GroupRuntime rt = runtimes.get(groupId);
        if (rt == null) {
            return;
        }
        for (GroupMember gm : memberMapper.selectList(
                new QueryWrapper<GroupMember>().eq("group_id", groupId))) {
            MemberState ms = rt.members.get(gm.getPersonaId());
            if (ms != null) {
                ms.activity = gm.getActivity() == null ? 50 : gm.getActivity();
                ms.muted = gm.getIsMuted() != null && gm.getIsMuted() == 1;
            }
        }
    }

    /** 剧目完结：停机并移除运行时（观众端由调用方广播 ended）。 */
    public void end(Long groupId) {
        GroupRuntime rt = runtimes.remove(groupId);
        if (rt != null) {
            rt.ended = true;
            rt.executor.shutdownNow();
        }
    }

    // ==================== 事件循环 ====================

    private void runLoop(GroupRuntime rt) {
        try {
            int burst = 0;
            boolean firstRound = true;
            while (burst < MAX_BURST && !rt.paused && !rt.ended) {
                TheaterGroup group = groupMapper.selectById(rt.groupId);
                if (group == null || !"LIVE".equals(group.getStatus())) {
                    break;
                }
                MemberState speaker = pickSpeaker(rt);
                if (speaker == null) {
                    break; // 全员禁言
                }
                if (!speak(rt, group, speaker)) {
                    break; // 生成失败，本轮到此为止
                }
                burst++;
                if (rt.paused || rt.ended) {
                    break;
                }
                Thread.sleep(ThreadLocalRandom.current().nextLong(SPEAK_GAP_MIN_MS, SPEAK_GAP_MAX_MS));
                // 连发判定：首轮必回（用户刚说话必须有人接梗），之后按概率
                if (!firstRound && ThreadLocalRandom.current().nextDouble() >= continueProbability(rt, group)) {
                    break;
                }
                firstRound = false;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[Engine] 剧场 {} 事件循环异常: {}", rt.groupId, e.getMessage(), e);
        } finally {
            rt.looping.set(false);
        }
    }

    /** 加权选人：活跃度为权重，排除上一发言者，10% 纯随机。 */
    private MemberState pickSpeaker(GroupRuntime rt) {
        List<MemberState> candidates = rt.members.values().stream()
                .filter(m -> !m.muted)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        List<MemberState> pool = candidates;
        if (candidates.size() > 1 && rt.lastSpeakerId != null) {
            List<MemberState> filtered = candidates.stream()
                    .filter(m -> !m.persona.getId().equals(rt.lastSpeakerId))
                    .toList();
            if (!filtered.isEmpty()) {
                pool = filtered;
            }
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextInt(100) < 10) {
            return pool.get(rnd.nextInt(pool.size()));
        }
        int total = pool.stream().mapToInt(m -> weight(m)).sum();
        int dart = rnd.nextInt(total);
        for (MemberState m : pool) {
            dart -= weight(m);
            if (dart < 0) {
                return m;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private int weight(MemberState m) {
        return Math.max(1, m.activity);
    }

    /** p = 0.35 + 平均活跃度×0.4 + 热度×0.15，上限 0.9。 */
    private double continueProbability(GroupRuntime rt, TheaterGroup group) {
        double avgActivity = rt.members.values().stream()
                .mapToInt(m -> m.activity).average().orElse(50) / 100.0;
        double heat = Math.min(100, group.getHeat() == null ? 0 : group.getHeat()) / 100.0;
        return Math.min(0.9, 0.35 + avgActivity * 0.4 + heat * 0.15);
    }

    /** 一位演员发言一轮：上下文 → 流式生成 → 审核闸 → 落库 → 广播 → 热度。 */
    private boolean speak(GroupRuntime rt, TheaterGroup group, MemberState speaker) {
        Persona persona = speaker.persona;
        Long pid = persona.getId();

        // 上下文：最近 24 条正序历史 + 待注入指令
        List<Message> history = messageMapper.selectList(new QueryWrapper<Message>()
                .eq("group_id", rt.groupId).orderByDesc("id").last("LIMIT " + HISTORY_WINDOW));
        Collections.reverse(history);
        List<String> directives = new ArrayList<>();
        String d;
        while ((d = rt.pendingDirectives.poll()) != null) {
            directives.add(d);
        }

        sseHub.typing(rt.groupId, pid, persona.getName(), persona.getAvatar());

        ChatContext ctx = new ChatContext(persona, group.getScenarioSnapshot(),
                group.getDirectorNote(), history, directives);
        LlmGateway.Reply reply = gateway.chat(ctx, rt.groupId,
                delta -> sseHub.chunk(rt.groupId, pid, delta));

        String text = reply.text() == null ? "" : reply.text().trim();
        if (text.isEmpty()) {
            return false;
        }

        // AI 输出过 DFA：命中则整句替换为占位
        if (moderation.screen(text, "AI_MESSAGE", rt.groupId) != null) {
            text = "（该台词未通过内容审查，已被导演组删剪）";
        }

        // 情绪启发式标注
        String emotion = EmotionUtil.classify(text, persona.getArchetype());

        Message msg = new Message();
        msg.setGroupId(rt.groupId);
        msg.setSenderType("PERSONA");
        msg.setSenderId(pid);
        msg.setSenderName(persona.getName());
        msg.setSenderAvatar(persona.getAvatar());
        msg.setContent(text);
        msg.setEmotion(emotion);
        msg.setLikeCount(0);
        msg.setIsHighlight(0);
        messageMapper.insert(msg);

        // 热度上浮：基础 +1 + 情绪加成，封顶 100
        int bonus = 1 + EmotionUtil.intensityBonus(emotion);
        int newHeat = Math.min(100, (group.getHeat() == null ? 0 : group.getHeat()) + bonus);
        group.setHeat(newHeat);
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>().eq("id", rt.groupId)
                .setSql("message_count = message_count + 1")
                .setSql("heat = " + newHeat));

        sseHub.message(rt.groupId, msg);
        sseHub.heat(rt.groupId, newHeat);
        rt.lastSpeakerId = pid;

        // Real→Mock 降级提示（一次降级只提示一次）
        if (reply.upstreamError() != null && !rt.degradedNotified) {
            rt.degradedNotified = true;
            sseHub.system(rt.groupId, "AI 上游异常，已切换离线演员救场");
        }
        return true;
    }

    /** 惰性创建运行时：首次触发时从 DB 加载剧组（LIVE 且有成员才算有效）。 */
    private GroupRuntime runtime(Long groupId) {
        return runtimes.computeIfAbsent(groupId, id -> {
            TheaterGroup group = groupMapper.selectById(id);
            if (group == null || !"LIVE".equals(group.getStatus())) {
                return null;
            }
            List<GroupMember> members = memberMapper.selectList(
                    new QueryWrapper<GroupMember>().eq("group_id", id));
            if (members.isEmpty()) {
                return null;
            }
            GroupRuntime rt = new GroupRuntime(id);
            for (GroupMember gm : members) {
                Persona p = personaMapper.selectById(gm.getPersonaId());
                if (p != null) {
                    rt.members.put(p.getId(), new MemberState(p,
                            gm.getActivity() == null ? 50 : gm.getActivity(),
                            gm.getIsMuted() != null && gm.getIsMuted() == 1));
                }
            }
            log.info("[Engine] 剧场 {} 运行时已加载，剧组 {} 人", id, rt.members.size());
            return rt.members.isEmpty() ? null : rt;
        });
    }
}
