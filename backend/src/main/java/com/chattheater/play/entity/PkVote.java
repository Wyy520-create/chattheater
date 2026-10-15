package com.chattheater.play.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pk_vote")
public class PkVote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pkId;

    private Long userId;

    /** A / B */
    private String side;

    private LocalDateTime createdAt;
}
