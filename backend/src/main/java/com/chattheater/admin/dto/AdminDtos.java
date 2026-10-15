package com.chattheater.admin.dto;

import lombok.Data;

import java.util.List;

/** 运营看板的视图对象。 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 运营总览数字带。 */
    @Data
    public static class OverviewVO {
        private long users;
        private long liveTheaters;
        private long endedTheaters;
        private long personas;
        private long messages;
        private long todayNewUsers;
        private long todayNewMessages;
        /** 审核拦截次数（review_log 的 BLOCK）。 */
        private long blockedCount;
        /** 当前 Mock 占比（百分数 0~100，保留 1 位小数由前端处理）。 */
        private Double mockRatio;
    }

    /** LLM 成本看板。 */
    @Data
    public static class CostVO {
        private long totalCalls;
        private long okCalls;
        private long mockCalls;
        private long realCalls;
        /** Mock 占比 0~100。 */
        private double mockRatio;
        private long promptTokens;
        private long completionTokens;
        private long avgLatencyMs;
        private List<DailyCost> daily;
    }

    @Data
    public static class DailyCost {
        private String day;
        private long calls;
        private long mockCalls;
        private long tokens;
    }

    @Data
    public static class WordReq {
        private String word;
        private String category;
    }
}
