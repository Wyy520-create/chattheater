package com.chattheater.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chattheater.admin.dto.AdminDtos.CostVO;
import com.chattheater.admin.dto.AdminDtos.DailyCost;
import com.chattheater.admin.dto.AdminDtos.OverviewVO;
import com.chattheater.ai.entity.LlmUsage;
import com.chattheater.ai.mapper.LlmUsageMapper;
import com.chattheater.common.BizException;
import com.chattheater.moderation.ModerationService;
import com.chattheater.moderation.entity.ReviewLog;
import com.chattheater.moderation.entity.SensitiveWord;
import com.chattheater.moderation.mapper.ReviewLogMapper;
import com.chattheater.moderation.mapper.SensitiveWordMapper;
import com.chattheater.persona.entity.Persona;
import com.chattheater.persona.mapper.PersonaMapper;
import com.chattheater.theater.entity.Message;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.mapper.MessageMapper;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.user.entity.User;
import com.chattheater.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运营看板：平台总览 / LLM 成本 / 敏感词 CRUD / 审核日志 / 人设上下架。
 * 所有端点仅 ADMIN（Controller 里 AdminGuard.check()）。
 */
@Service
public class AdminService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final TheaterGroupMapper groupMapper;
    private final PersonaMapper personaMapper;
    private final MessageMapper messageMapper;
    private final LlmUsageMapper usageMapper;
    private final SensitiveWordMapper wordMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final ModerationService moderation;

    public AdminService(UserMapper userMapper, TheaterGroupMapper groupMapper,
                        PersonaMapper personaMapper, MessageMapper messageMapper,
                        LlmUsageMapper usageMapper, SensitiveWordMapper wordMapper,
                        ReviewLogMapper reviewLogMapper, ModerationService moderation) {
        this.userMapper = userMapper;
        this.groupMapper = groupMapper;
        this.personaMapper = personaMapper;
        this.messageMapper = messageMapper;
        this.usageMapper = usageMapper;
        this.wordMapper = wordMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.moderation = moderation;
    }

    public OverviewVO overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        OverviewVO vo = new OverviewVO();
        vo.setUsers(userMapper.selectCount(null));
        vo.setLiveTheaters(groupMapper.selectCount(new QueryWrapper<TheaterGroup>()
                .eq("status", "LIVE")));
        vo.setEndedTheaters(groupMapper.selectCount(new QueryWrapper<TheaterGroup>()
                .eq("status", "ENDED")));
        vo.setPersonas(personaMapper.selectCount(null));
        vo.setMessages(messageMapper.selectCount(null));
        vo.setTodayNewUsers(userMapper.selectCount(new QueryWrapper<User>()
                .ge("created_at", todayStart)));
        vo.setTodayNewMessages(messageMapper.selectCount(new QueryWrapper<Message>()
                .ge("created_at", todayStart)));
        vo.setBlockedCount(reviewLogMapper.selectCount(new QueryWrapper<ReviewLog>()
                .eq("action", "BLOCK")));

        long total = usageMapper.selectCount(null);
        long mock = usageMapper.selectCount(new QueryWrapper<LlmUsage>().eq("mock", 1));
        vo.setMockRatio(total == 0 ? 0.0 : mock * 100.0 / total);
        return vo;
    }

    /** LLM 成本：全量聚合 + 近 7 日趋势。 */
    public CostVO cost() {
        CostVO vo = new CostVO();
        Map<String, Object> total = firstRow(usageMapper.selectMaps(new QueryWrapper<LlmUsage>()
                .select("COUNT(*) AS totalCalls", "IFNULL(SUM(ok), 0) AS okCalls",
                        "IFNULL(SUM(mock), 0) AS mockCalls",
                        "IFNULL(SUM(prompt_tokens), 0) AS promptTokens",
                        "IFNULL(SUM(completion_tokens), 0) AS completionTokens",
                        "IFNULL(AVG(latency_ms), 0) AS avgLatencyMs")));
        vo.setTotalCalls(asLong(total.get("totalCalls")));
        vo.setOkCalls(asLong(total.get("okCalls")));
        vo.setMockCalls(asLong(total.get("mockCalls")));
        vo.setRealCalls(vo.getTotalCalls() - vo.getMockCalls());
        vo.setMockRatio(vo.getTotalCalls() == 0 ? 0
                : Math.round(vo.getMockCalls() * 10000.0 / vo.getTotalCalls()) / 100.0);
        vo.setPromptTokens(asLong(total.get("promptTokens")));
        vo.setCompletionTokens(asLong(total.get("completionTokens")));
        vo.setAvgLatencyMs(asLong(total.get("avgLatencyMs")));

        List<DailyCost> daily = new ArrayList<>();
        List<Map<String, Object>> rows = usageMapper.selectMaps(new QueryWrapper<LlmUsage>()
                .select("DATE(created_at) AS day", "COUNT(*) AS calls",
                        "IFNULL(SUM(mock), 0) AS mockCalls",
                        "IFNULL(SUM(prompt_tokens + completion_tokens), 0) AS tokens")
                .apply("created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)")
                .groupBy("DATE(created_at)").orderByAsc("DATE(created_at)"));
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                DailyCost d = new DailyCost();
                Object day = row.get("day");
                d.setDay(day == null ? "" : day.toString());
                d.setCalls(asLong(row.get("calls")));
                d.setMockCalls(asLong(row.get("mockCalls")));
                d.setTokens(asLong(row.get("tokens")));
                daily.add(d);
            }
        }
        vo.setDaily(daily);
        return vo;
    }

    // ==================== 敏感词 CRUD ====================

    public Page<SensitiveWord> words(int page, int size) {
        return wordMapper.selectPage(new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)),
                new QueryWrapper<SensitiveWord>().orderByDesc("hit_count").orderByAsc("id"));
    }

    public SensitiveWord addWord(String word, String category) {
        String w = word == null ? "" : word.trim();
        if (w.isEmpty() || w.length() > 50) {
            throw BizException.bad("敏感词不能为空，且不超过 50 字");
        }
        Long exists = wordMapper.selectCount(new QueryWrapper<SensitiveWord>().eq("word", w));
        if (exists > 0) {
            throw BizException.bad("该敏感词已存在");
        }
        SensitiveWord row = new SensitiveWord();
        row.setWord(w);
        row.setCategory(category == null || category.isBlank() ? "通用" : category.trim());
        row.setHitCount(0);
        row.setStatus(1);
        wordMapper.insert(row);
        moderation.reload();
        return row;
    }

    public void deleteWord(Long id) {
        SensitiveWord word = wordMapper.selectById(id);
        if (word == null) {
            throw BizException.notFound("敏感词不存在");
        }
        wordMapper.deleteById(id);
        moderation.reload();
    }

    // ==================== 审核日志与人设管理 ====================

    public Page<ReviewLog> logs(int page, int size) {
        return reviewLogMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)),
                new QueryWrapper<ReviewLog>().orderByDesc("id"));
    }

    /** 全量人设（含已下架，供运营巡查）。 */
    public Page<Persona> personas(int page, int size, String keyword, Integer status) {
        QueryWrapper<Persona> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like("name", keyword.trim());
        }
        if (status != null) {
            qw.eq("status", status);
        }
        return personaMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)),
                qw.orderByDesc("id"));
    }

    /** 人设上下架（下架即从市场消失，进行中的剧组不受影响——快照已加载）。 */
    public Persona personaStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.bad("status 只能是 0（下架）或 1（上架）");
        }
        Persona persona = personaMapper.selectById(id);
        if (persona == null) {
            throw BizException.notFound("人设不存在");
        }
        personaMapper.update(null, new UpdateWrapper<Persona>().eq("id", id)
                .set("status", status));
        persona.setStatus(status);
        return persona;
    }

    // ==================== 私有辅助 ====================

    private static Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() || rows.get(0) == null
                ? Map.of() : rows.get(0);
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }
}
