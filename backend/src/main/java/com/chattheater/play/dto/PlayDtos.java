package com.chattheater.play.dto;

import com.chattheater.play.entity.PkMatch;
import com.chattheater.play.entity.TheaterPoll;
import com.chattheater.theater.dto.TheaterDtos.TheaterVO;
import lombok.Data;

import java.util.List;

/** play 模块（观众投票改剧本 / 多剧组 PK）的请求与视图对象。 */
public final class PlayDtos {

    private PlayDtos() {
    }

    // ==================== 投票改剧本 ====================

    @Data
    public static class CreatePollReq {
        /** 投票主题（剧情走向问题），1~60 字。 */
        private String topic;
        /** 2~4 个走向选项，每项 1~20 字。 */
        private List<String> options;
        /** 投票时长秒数：15~120，缺省 30。 */
        private Integer durationSec;
    }

    @Data
    public static class PollVoteReq {
        /** 选项下标，从 0 开始。 */
        private Integer optionIdx;
    }

    @Data
    public static class PollVO {
        private Long id;
        private Long theaterId;
        private String topic;
        private List<String> options;
        /** 每个选项的实时票数，与 options 同下标。 */
        private List<Integer> counts;
        private Integer totalVotes;
        /** 截止时间的 epoch 毫秒；OPEN 时有效。 */
        private Long endsAt;
        /** OPEN / CLOSED。 */
        private String status;
        /** 结算胜出的选项下标；平局或未结算为 null。 */
        private Integer winnerIdx;
        /** 当前用户是否已投（游客恒 false）。 */
        private boolean myVoted;
        private Integer myOption;

        public static PollVO from(TheaterPoll p, List<String> options,
                                  List<Integer> counts, boolean myVoted, Integer myOption) {
            PollVO vo = new PollVO();
            vo.setId(p.getId());
            vo.setTheaterId(p.getTheaterId());
            vo.setTopic(p.getTopic());
            vo.setOptions(options);
            vo.setCounts(counts);
            vo.setTotalVotes(counts == null ? 0 : counts.stream().mapToInt(Integer::intValue).sum());
            vo.setEndsAt(p.getEndsAt() == null ? null : p.getEndsAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            vo.setStatus(p.getStatus());
            vo.setWinnerIdx(p.getWinnerIdx());
            vo.setMyVoted(myVoted);
            vo.setMyOption(myOption);
            return vo;
        }
    }

    // ==================== 多剧组 PK ====================

    @Data
    public static class PkCreateReq {
        private String title;
        private Long theaterAId;
        private Long theaterBId;
        /** 擂台时长秒数：300~3600，缺省 900。 */
        private Integer durationSec;
    }

    @Data
    public static class PkVoteReq {
        /** A / B。 */
        private String side;
    }

    @Data
    public static class PkVO {
        private Long id;
        private String title;
        /** OPEN / CLOSED。 */
        private String status;
        /** 结算结果：A / B / T（平局）；未结算为 null。 */
        private String winner;
        /** 截止时间的 epoch 毫秒；OPEN 时有效。 */
        private Long endsAt;
        private TheaterVO theaterA;
        private TheaterVO theaterB;
        /** 结算得分：热度增量 + 票数×3；未结算时暂为票数×3。 */
        private Integer scoreA;
        private Integer scoreB;
        private Integer votesA;
        private Integer votesB;
        /** 当前用户是否已投（游客恒 false）。 */
        private boolean myVoted;
        private String mySide;

        public static PkVO of(PkMatch m, TheaterVO a, TheaterVO b,
                              Integer scoreA, Integer scoreB, boolean myVoted, String mySide) {
            PkVO vo = new PkVO();
            vo.setId(m.getId());
            vo.setTitle(m.getTitle());
            vo.setStatus(m.getStatus());
            vo.setWinner(m.getWinner());
            vo.setEndsAt(m.getEndsAt() == null ? null : m.getEndsAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            vo.setTheaterA(a);
            vo.setTheaterB(b);
            vo.setScoreA(scoreA);
            vo.setScoreB(scoreB);
            vo.setVotesA(m.getVotesA());
            vo.setVotesB(m.getVotesB());
            vo.setMyVoted(myVoted);
            vo.setMySide(mySide);
            return vo;
        }
    }
}
