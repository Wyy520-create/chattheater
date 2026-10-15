package com.chattheater.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("persona")
public class Persona {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** NULL = 官方出品。 */
    private Long ownerId;

    private String name;

    private String avatar;

    /** 原型：毒舌/杠精/慈祥/卷王/社恐/吃瓜/显眼包/暴躁/阴阳/财迷。 */
    private String archetype;

    private String tagline;

    private String traits;

    private String speakingStyle;

    private String background;

    private String taboo;

    private Integer isOfficial;

    private Integer useCount;

    private Integer likeCount;

    /** 1 上架 / 0 封禁下架。 */
    private Integer status;

    private LocalDateTime createdAt;
}
