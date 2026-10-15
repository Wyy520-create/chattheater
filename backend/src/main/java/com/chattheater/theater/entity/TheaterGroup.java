package com.chattheater.theater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("theater_group")
public class TheaterGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String title;

    private Long scenarioId;

    /** 场景 backdrop 的冗余快照（防场景记录变化/删除影响进行中的剧目）。 */
    private String scenarioSnapshot;

    private String directorNote;

    /** LIVE / ENDED。 */
    private String status;

    /** PUBLIC / PRIVATE。 */
    private String visibility;

    private Integer heat;

    private Integer messageCount;

    private Integer likeCount;

    private Integer viewCount;

    private String endedReason;

    private LocalDateTime createdAt;

    private LocalDateTime endedAt;
}
