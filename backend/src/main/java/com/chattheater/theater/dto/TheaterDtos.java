package com.chattheater.theater.dto;

import com.chattheater.theater.entity.GroupMember;
import com.chattheater.theater.entity.Message;
import com.chattheater.theater.entity.TheaterGroup;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** theater 模块的请求/视图对象。 */
public final class TheaterDtos {

    private TheaterDtos() {
    }

    // ==================== 请求 ====================

    @Data
    public static class CreateReq {
        private String title;
        private Long scenarioId;
        /** 2~6 个演员。 */
        private List<Long> personaIds;
        /** PUBLIC / PRIVATE。 */
        private String visibility;
        private String directorNote;
    }

    @Data
    public static class PostMessageReq {
        private String content;
    }

    /** 导演台操作：DIRECTIVE/EVENT_CARD/PAUSE/RESUME/MUTE/UNMUTE/ACTIVITY。 */
    @Data
    public static class DirectorReq {
        private String type;
        private String content;
        private String eventKey;
        private Long personaId;
        private Integer activity;
    }

    @Data
    public static class EndReq {
        private String reason;
    }

    // ==================== 视图 ====================

    @Data
    public static class MessageVO {
        private Long id;
        private Long groupId;
        private String senderType;
        private Long senderId;
        private String senderName;
        private String senderAvatar;
        private String content;
        private String emotion;
        private Integer likeCount;
        private Integer isHighlight;
        private LocalDateTime createdAt;
        /** 当前用户是否已赞（游客恒 false）。 */
        private boolean liked;

        public static MessageVO from(Message m, boolean liked) {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setGroupId(m.getGroupId());
            vo.setSenderType(m.getSenderType());
            vo.setSenderId(m.getSenderId());
            vo.setSenderName(m.getSenderName());
            vo.setSenderAvatar(m.getSenderAvatar());
            vo.setContent(m.getContent());
            vo.setEmotion(m.getEmotion());
            vo.setLikeCount(m.getLikeCount());
            vo.setIsHighlight(m.getIsHighlight());
            vo.setCreatedAt(m.getCreatedAt());
            vo.setLiked(liked);
            return vo;
        }
    }

    @Data
    public static class MemberVO {
        private Long personaId;
        private String name;
        private String avatar;
        private String archetype;
        private String tagline;
        private Integer activity;
        private boolean muted;

        public static MemberVO from(GroupMember gm, com.chattheater.persona.entity.Persona p) {
            MemberVO vo = new MemberVO();
            vo.setPersonaId(p.getId());
            vo.setName(p.getName());
            vo.setAvatar(p.getAvatar());
            vo.setArchetype(p.getArchetype());
            vo.setTagline(p.getTagline());
            vo.setActivity(gm.getActivity());
            vo.setMuted(gm.getIsMuted() != null && gm.getIsMuted() == 1);
            return vo;
        }
    }

    @Data
    public static class TheaterVO {
        private Long id;
        private Long ownerId;
        private String ownerName;
        private String title;
        private Long scenarioId;
        private String scenarioSnapshot;
        private String directorNote;
        private String status;
        private String visibility;
        private Integer heat;
        private Integer messageCount;
        private Integer likeCount;
        private Integer viewCount;
        private String endedReason;
        private LocalDateTime createdAt;
        private LocalDateTime endedAt;
        private boolean liked;
        private boolean favorited;
        /** 当前在线观众数。 */
        private Integer audience;
        private List<MemberVO> members;

        public static TheaterVO from(TheaterGroup g, String ownerName, List<MemberVO> members) {
            TheaterVO vo = new TheaterVO();
            vo.setId(g.getId());
            vo.setOwnerId(g.getOwnerId());
            vo.setOwnerName(ownerName);
            vo.setTitle(g.getTitle());
            vo.setScenarioId(g.getScenarioId());
            vo.setScenarioSnapshot(g.getScenarioSnapshot());
            vo.setDirectorNote(g.getDirectorNote());
            vo.setStatus(g.getStatus());
            vo.setVisibility(g.getVisibility());
            vo.setHeat(g.getHeat());
            vo.setMessageCount(g.getMessageCount());
            vo.setLikeCount(g.getLikeCount());
            vo.setViewCount(g.getViewCount());
            vo.setEndedReason(g.getEndedReason());
            vo.setCreatedAt(g.getCreatedAt());
            vo.setEndedAt(g.getEndedAt());
            vo.setMembers(members);
            return vo;
        }
    }
}
