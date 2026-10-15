package com.chattheater.play.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pk_match")
public class PkMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long ownerId;

    private Long theaterAId;

    private Long theaterBId;

    /** 发起时的热度快照，结算时算增量。 */
    private Integer heatAStart;

    private Integer heatBStart;

    private Integer votesA;

    private Integer votesB;

    /** OPEN / CLOSED */
    private String status;

    /** 结算结果：A / B / T（平局）。 */
    private String winner;

    private LocalDateTime endsAt;

    private LocalDateTime createdAt;
}
