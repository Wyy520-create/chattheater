package com.chattheater.theater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("message_like")
public class MessageLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long messageId;
}
