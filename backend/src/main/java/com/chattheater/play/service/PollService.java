package com.chattheater.play.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chattheater.common.BizException;
import com.chattheater.moderation.ModerationService;
import com.chattheater.play.dto.PlayDtos.CreatePollReq;
import com.chattheater.play.dto.PlayDtos.PollVO;
import com.chattheater.play.entity.PollVote;
import com.chattheater.play.entity.TheaterPoll;
import com.chattheater.play.mapper.PollVoteMapper;
import com.chattheater.play.mapper.TheaterPollMapper;
import com.chattheater.theater.engine.ChatEngine;
import com.chattheater.theater.entity.TheaterGroup;
import com.chattheater.theater.mapper.TheaterGroupMapper;
import com.chattheater.theater.sse.SseHub;
import com.chattheater.user.entity.User;
import com.chattheater.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 观众投票改剧本：导演发起剧情走向投票，观众一人一票，
 * 截止后把胜出走向注入导演指令，AI 演员围绕新走向继续演。
 *
 * 结算策略：懒结算（任意一次读/写触发）+ 定时任务兜底扫尾，
 * 与聊天引擎主循环零耦合。
 */
@Slf4j
@Service
public class PollService {

    private static final int MAX_TOPIC_LEN = 60;
    private static final int MAX_OPTION_LEN = 20;
    private static final int MIN_DURATION_SEC = 15;
    private static final int MAX_DURATION_SEC = 120;
    private static final int DEFAULT_DURATION_SEC = 30;

    private final TheaterPollMapper pollMapper;
    private final PollVoteMapper voteMapper;
    private final TheaterGroupMapper groupMapper;
    private final UserService userService;
    private final ModerationService moderation;
    private final ChatEngine engine;
    private final SseHub sseHub;

    public PollService(TheaterPollMapper pollMapper, PollVoteMapper voteMapper,
                       TheaterGroupMapper groupMapper, UserService userService,
                       ModerationService moderation, ChatEngine engine, SseHub sseHub) {
        this.pollMapper = pollMapper;
        this.voteMapper = voteMapper;
        this.groupMapper = groupMapper;
        this.userService = userService;
        this.moderation = moderation;
        this.engine = engine;
        this.sseHub = sseHub;
    }

    // ==================== 发起投票（导演） ====================

    public PollVO create(User me, Long groupId, CreatePollReq req) {
        TheaterGroup group = getOrThrow(groupId);
        if (!group.getOwnerId().equals(me.getId()) && !"ADMIN".equals(me.getRole())) {
            throw BizException.forbidden("只有本剧导演才能发起投票");
        }
        if (!"LIVE".equals(group.getStatus())) {
            throw BizException.bad("本剧已杀青，无法发起投票");
        }
        Long open = pollMapper.selectCount(new QueryWrapper<TheaterPoll>()
                .eq("theater_id", groupId).eq("status", "OPEN"));
        if (open > 0) {
            throw BizException.bad("已有一场进行中的投票，等它结束再发");
        }
        String topic = req.getTopic() == null ? "" : req.getTopic().trim();
        if (topic.isEmpty() || topic.length() > MAX_TOPIC_LEN) {
            throw BizException.bad("投票主题不能为空，且不超过 " + MAX_TOPIC_LEN + " 字");
        }
        List<String> options = new ArrayList<>();
        if (req.getOptions() != null) {
            for (String raw : req.getOptions()) {
                String opt = raw == null ? "" : raw.trim();
                if (opt.isEmpty() || opt.length() > MAX_OPTION_LEN) {
                    throw BizException.bad("每个选项 1~" + MAX_OPTION_LEN + " 字");
                }
                options.add(opt);
            }
        }
        if (options.size() < 2 || options.size() > 4) {
            throw BizException.bad("剧情走向选项需要 2~4 个");
        }
        int duration = req.getDurationSec() == null ? DEFAULT_DURATION_SEC : req.getDurationSec();
        if (duration < MIN_DURATION_SEC || duration > MAX_DURATION_SEC) {
            throw BizException.bad("投票时长 " + MIN_DURATION_SEC + "~" + MAX_DURATION_SEC + " 秒");
        }
        moderation.assertClean(topic + " " + String.join(" ", options), "POLL", me.getId());

        TheaterPoll poll = new TheaterPoll();
        poll.setTheaterId(groupId);
        poll.setDirectorId(me.getId());
        poll.setTopic(topic);
        poll.setOptionsJson(String.join("\n", options));
        poll.setEndsAt(LocalDateTime.now().plusSeconds(duration));
        poll.setStatus("OPEN");
        pollMapper.insert(poll);

        sseHub.system(groupId, "🗳 导演发起投票改剧本：" + topic);
        PollVO vo = voOf(poll, me);
        sseHub.poll(groupId, vo);
        log.info("[Poll] 剧场 {} 发起投票 {}「{}」，时长 {}s，选项 {}",
                groupId, poll.getId(), topic, duration, options);
        return vo;
    }

    // ==================== 查询当前投票（懒结算） ====================

    public PollVO active(Long groupId, User viewer) {
        getOrThrow(groupId);
        TheaterPoll poll = pollMapper.selectOne(new QueryWrapper<TheaterPoll>()
                .eq("theater_id", groupId).orderByDesc("id").last("LIMIT 1"));
        if (poll == null) {
            return null;
        }
        if ("OPEN".equals(poll.getStatus()) && poll.getEndsAt().isBefore(LocalDateTime.now())) {
            settle(poll);
            poll = pollMapper.selectById(poll.getId());
        }
        return voOf(poll, viewer);
    }

    // ==================== 观众投票 ====================

    public PollVO vote(User me, Long pollId, Integer optionIdx) {
        TheaterPoll poll = pollMapper.selectById(pollId);
        if (poll == null) {
            throw BizException.notFound("投票不存在");
        }
        if ("OPEN".equals(poll.getStatus()) && poll.getEndsAt().isBefore(LocalDateTime.now())) {
            settle(poll);
            throw BizException.bad("投票刚截止，下次手快点");
        }
        if (!"OPEN".equals(poll.getStatus())) {
            throw BizException.bad("投票已截止");
        }
        List<String> options = optionsOf(poll);
        if (optionIdx == null || optionIdx < 0 || optionIdx >= options.size()) {
            throw BizException.bad("选项不存在");
        }
        PollVote vote = new PollVote();
        vote.setPollId(pollId);
        vote.setUserId(me.getId());
        vote.setOptionIdx(optionIdx);
        try {
            voteMapper.insert(vote);
        } catch (DuplicateKeyException e) {
            throw BizException.bad("一人一票，你已经投过了");
        }
        userService.addXp(me.getId(), 1);

        TheaterPoll fresh = pollMapper.selectById(pollId);
        PollVO vo = voOf(fresh, me);
        sseHub.poll(poll.getTheaterId(), vo);
        return vo;
    }

    // ==================== 结算 ====================

    /**
     * 结算：统计票数 → 定胜负（平局 winnerIdx=null）→
     * 把胜出走向注入导演指令让演员改剧本，并向直播间广播结果。
     */
    private void settle(TheaterPoll poll) {
        List<Integer> counts = countsOf(poll.getId(), optionsOf(poll).size());
        int winnerIdx = -1;
        int best = -1;
        boolean tie = false;
        for (int i = 0; i < counts.size(); i++) {
            int c = counts.get(i);
            if (c > best) {
                best = c;
                winnerIdx = i;
                tie = false;
            } else if (c == best) {
                tie = true;
            }
        }
        Integer winner = (winnerIdx >= 0 && best > 0 && !tie) ? winnerIdx : null;

        pollMapper.update(null, new UpdateWrapper<TheaterPoll>()
                .eq("id", poll.getId())
                .set("status", "CLOSED")
                .set("winner_idx", winner));
        groupMapper.update(null, new UpdateWrapper<TheaterGroup>()
                .eq("id", poll.getTheaterId())
                .setSql("heat = LEAST(100, heat + 5)"));

        List<String> options = optionsOf(poll);
        String text;
        if (winner == null) {
            text = "🗳 观众投票「" + poll.getTopic() + "」势均力敌，演员们自由发挥！";
            engine.pushDirective(poll.getTheaterId(),
                    "观众对「" + poll.getTopic() + "」意见持平，请自由演绎这个走向");
        } else {
            String win = options.get(winner);
            text = "🗳 观众投票「" + poll.getTopic() + "」结果：" + win + "！演员们改剧本！";
            engine.pushDirective(poll.getTheaterId(),
                    "观众投票结果：走向「" + win + "」，请围绕这个走向继续演出");
        }
        sseHub.system(poll.getTheaterId(), text);
        TheaterPoll fresh = pollMapper.selectById(poll.getId());
        sseHub.poll(poll.getTheaterId(), voOf(fresh, null));
        log.info("[Poll] 投票 {} 结算，winnerIdx={}", poll.getId(), winner);
    }

    /** 定时兜底：每 5 秒扫一遍过期的 OPEN 投票。 */
    @Scheduled(fixedDelay = 5000)
    public void settleOverdue() {
        List<TheaterPoll> overdue = pollMapper.selectList(new QueryWrapper<TheaterPoll>()
                .eq("status", "OPEN").lt("ends_at", LocalDateTime.now()));
        for (TheaterPoll p : overdue) {
            try {
                settle(p);
            } catch (Exception e) {
                log.warn("[Poll] 定时结算投票 {} 失败：{}", p.getId(), e.getMessage());
            }
        }
    }

    // ==================== 私有辅助 ====================

    private PollVO voOf(TheaterPoll poll, User viewer) {
        List<String> options = optionsOf(poll);
        List<Integer> counts = countsOf(poll.getId(), options.size());
        boolean myVoted = false;
        Integer myOption = null;
        if (viewer != null) {
            PollVote mine = voteMapper.selectOne(new QueryWrapper<PollVote>()
                    .eq("poll_id", poll.getId()).eq("user_id", viewer.getId()).last("LIMIT 1"));
            if (mine != null) {
                myVoted = true;
                myOption = mine.getOptionIdx();
            }
        }
        return PollVO.from(poll, options, counts, myVoted, myOption);
    }

    private List<String> optionsOf(TheaterPoll poll) {
        if (poll.getOptionsJson() == null || poll.getOptionsJson().isBlank()) {
            return List.of();
        }
        return Arrays.asList(poll.getOptionsJson().split("\n"));
    }

    private List<Integer> countsOf(Long pollId, int size) {
        List<PollVote> votes = voteMapper.selectList(new QueryWrapper<PollVote>().eq("poll_id", pollId));
        List<Integer> counts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            counts.add(0);
        }
        for (PollVote v : votes) {
            if (v.getOptionIdx() != null && v.getOptionIdx() >= 0 && v.getOptionIdx() < size) {
                counts.set(v.getOptionIdx(), counts.get(v.getOptionIdx()) + 1);
            }
        }
        return counts;
    }

    private TheaterGroup getOrThrow(Long groupId) {
        TheaterGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BizException.notFound("剧场不存在");
        }
        return group;
    }
}
