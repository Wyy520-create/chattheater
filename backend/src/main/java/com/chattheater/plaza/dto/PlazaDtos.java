package com.chattheater.plaza.dto;

import com.chattheater.theater.entity.TheaterGroup;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 广场模块的视图对象：剧场卡片 / 名场面 / 全站统计。 */
public final class PlazaDtos {

    private PlazaDtos() {
    }

    /** 广场卡片（列表用，比 TheaterVO 轻量）。 */
    @Data
    public static class PlazaCardVO {
        private Long id;
        private String title;
        private String ownerName;
        private String status;
        private Integer heat;
        private Integer messageCount;
        private Integer likeCount;
        private Integer viewCount;
        private String endedReason;
        private LocalDateTime createdAt;
        private LocalDateTime endedAt;
        /** 当前围观人数（LIVE 才有意义）。 */
        private Integer audience;
        /** 剧组名字串，如 "毒舌·Lisa / 杠精·老王"。 */
        private String castSummary;
        /** 舞台快照摘要（卡片文案）。 */
        private String scenarioBrief;

        public static PlazaCardVO from(TheaterGroup g, String ownerName,
                                       String castSummary, Integer audience) {
            PlazaCardVO vo = new PlazaCardVO();
            vo.setId(g.getId());
            vo.setTitle(g.getTitle());
            vo.setOwnerName(ownerName);
            vo.setStatus(g.getStatus());
            vo.setHeat(g.getHeat());
            vo.setMessageCount(g.getMessageCount());
            vo.setLikeCount(g.getLikeCount());
            vo.setViewCount(g.getViewCount());
            vo.setEndedReason(g.getEndedReason());
            vo.setCreatedAt(g.getCreatedAt());
            vo.setEndedAt(g.getEndedAt());
            vo.setAudience(audience);
            vo.setCastSummary(castSummary);
            String snap = g.getScenarioSnapshot();
            vo.setScenarioBrief(snap == null ? "" : (snap.length() <= 60 ? snap : snap.substring(0, 60) + "……"));
            return vo;
        }
    }

    /** 名场面馆：完结剧目的台本回放。 */
    @Data
    public static class HallVO {
        private PlazaCardVO theater;
        private List<com.chattheater.theater.dto.TheaterDtos.MessageVO> highlights;
        /** 高光是否为空（前端决定展示"暂无名场面"占位）。 */
        private boolean hasHighlights;
    }

    /** 全站统计（广场顶部数字带）。 */
    @Data
    public static class StatsVO {
        private long liveCount;
        private long endedCount;
        private long totalMessages;
        private long totalUsers;
        private long totalPersonas;
        private int onlineAudience;
    }
}
