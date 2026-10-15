package com.chattheater.theater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    /** USER / PERSONA / DIRECTOR / SYSTEM。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String senderType;

    /** user id 或 persona id（SYSTEM 为 null）。 */
    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String content;

    /** joy/angry/sad/shock/jealousy/pride/neutral（仅 PERSONA 消息）。 */
    private String emotion;

    private Integer likeCount;

    private Integer isHighlight;

    private LocalDateTime createdAt;
}
