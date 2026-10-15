package com.chattheater.play.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("theater_poll")
public class TheaterPoll {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long theaterId;

    private Long directorId;

    /** 投票主题，如「剧情下一步往哪走？」 */
    private String topic;

    /** JSON 数组字符串，如 ["继续吃瓜","当场撕破脸","散场干饭"] */
    private String optionsJson;

    private LocalDateTime endsAt;

    /** OPEN / CLOSED */
    private String status;

    /** 结算后的胜选项下标。 */
    private Integer winnerIdx;

    private LocalDateTime createdAt;
}
