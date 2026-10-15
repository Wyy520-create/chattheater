package com.chattheater.theater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("group_member")
public class GroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long personaId;

    /** 活跃度 0-100：越高越容易被调度发言。 */
    private Integer activity;

    private Integer isMuted;
}
