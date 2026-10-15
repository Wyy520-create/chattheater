package com.chattheater.me.dto;

import com.chattheater.user.dto.LevelCalculator;
import com.chattheater.user.entity.User;
import lombok.Data;

/** 个人中心的视图对象。 */
public final class MeDtos {

    private MeDtos() {
    }

    /** 创作总览：等级成长条 + 各维度数字。 */
    @Data
    public static class OverviewVO {
        private Long userId;
        private String nickname;
        private String avatar;
        private int xp;
        private String levelName;
        private int nextLevelXp;
        private int levelProgress;
        private long liveTheaters;
        private long endedTheaters;
        private long personaCount;
        private long favoriteCount;
        /** 剧场获赞 + 人设获赞。 */
        private long receivedLikes;
        private long myMessageCount;

        public static OverviewVO from(User user, LevelCalculator.Level level) {
            OverviewVO vo = new OverviewVO();
            vo.setUserId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setXp(user.getXp() == null ? 0 : user.getXp());
            vo.setLevelName(level.name());
            vo.setNextLevelXp(level.nextXp());
            vo.setLevelProgress(level.progress());
            return vo;
        }
    }
}
