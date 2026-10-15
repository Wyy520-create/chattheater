package com.chattheater.scenario.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scenario")
public class Scenario {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 舞台设定：注入 AI 上下文的世界观描述。 */
    private String backdrop;

    /** 开场默认导演指令。 */
    private String openingDirective;

    private Integer suggestedCast;

    private Integer isOfficial;

    private Integer useCount;

    private LocalDateTime createdAt;
}
