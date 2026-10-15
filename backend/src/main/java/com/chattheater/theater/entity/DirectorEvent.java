package com.chattheater.theater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("director_event")
public class DirectorEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    /** DIRECTIVE / EVENT_CARD / PAUSE / RESUME / MUTE / UNMUTE / ACTIVITY / END。 */
    private String type;

    private String content;

    private Long personaId;

    private Long operatorId;

    private LocalDateTime createdAt;
}
