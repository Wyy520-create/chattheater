package com.chattheater.theater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("group_like")
public class GroupLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long groupId;
}
