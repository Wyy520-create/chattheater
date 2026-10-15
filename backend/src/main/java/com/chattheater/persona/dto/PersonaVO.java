package com.chattheater.persona.dto;

import com.chattheater.persona.entity.Persona;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonaVO {

    private Long id;
    private Long ownerId;
    private String name;
    private String avatar;
    private String archetype;
    private String tagline;
    private String traits;
    private String speakingStyle;
    private String background;
    private String taboo;
    private Integer isOfficial;
    private Integer useCount;
    private Integer likeCount;
    private Boolean liked;          // 当前用户是否点过赞（游客恒为 false）
    private LocalDateTime createdAt;

    public static PersonaVO from(Persona p, boolean liked) {
        PersonaVO vo = new PersonaVO();
        vo.setId(p.getId());
        vo.setOwnerId(p.getOwnerId());
        vo.setName(p.getName());
        vo.setAvatar(p.getAvatar());
        vo.setArchetype(p.getArchetype());
        vo.setTagline(p.getTagline());
        vo.setTraits(p.getTraits());
        vo.setSpeakingStyle(p.getSpeakingStyle());
        vo.setBackground(p.getBackground());
        vo.setTaboo(p.getTaboo());
        vo.setIsOfficial(p.getIsOfficial());
        vo.setUseCount(p.getUseCount());
        vo.setLikeCount(p.getLikeCount());
        vo.setLiked(liked);
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }
}
