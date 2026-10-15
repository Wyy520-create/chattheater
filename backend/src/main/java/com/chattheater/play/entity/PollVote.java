package com.chattheater.play.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_vote")
public class PollVote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pollId;

    private Long userId;

    /** 选项下标，从 0 开始。 */
    private Integer optionIdx;

    private LocalDateTime createdAt;
}
