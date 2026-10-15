package com.chattheater.user.dto;

import com.chattheater.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外用户视图（绝不携带 passwordHash）。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer xp;
    private String levelName;
    private Integer levelNextXp;
    private Integer levelProgress; // 0-100，当前等级到下一等级的进度
    private LocalDateTime createdAt;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setXp(user.getXp() == null ? 0 : user.getXp());
        vo.setCreatedAt(user.getCreatedAt());

        LevelCalculator.Level level = LevelCalculator.of(vo.getXp());
        vo.setLevelName(level.name());
        vo.setLevelNextXp(level.nextXp());
        vo.setLevelProgress(level.progress());
        return vo;
    }
}
