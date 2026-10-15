package com.chattheater.play.controller;

import com.chattheater.common.ApiResult;
import com.chattheater.play.dto.PlayDtos.CreatePollReq;
import com.chattheater.play.dto.PlayDtos.PkCreateReq;
import com.chattheater.play.dto.PlayDtos.PkVO;
import com.chattheater.play.dto.PlayDtos.PollVO;
import com.chattheater.play.dto.PlayDtos.PkVoteReq;
import com.chattheater.play.dto.PlayDtos.PollVoteReq;
import com.chattheater.play.service.PkService;
import com.chattheater.play.service.PollService;
import com.chattheater.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 新玩法接口：观众投票改剧本 + 多剧组 PK。 */
@RestController
@RequestMapping("/api")
public class PlayController {

    private final PollService pollService;
    private final PkService pkService;

    public PlayController(PollService pollService, PkService pkService) {
        this.pollService = pollService;
        this.pkService = pkService;
    }

    // ==================== 投票改剧本 ====================

    /** 导演发起投票。 */
    @PostMapping("/theaters/{id}/polls")
    public ApiResult<PollVO> createPoll(@PathVariable Long id, @RequestBody CreatePollReq req) {
        return ApiResult.ok(pollService.create(UserContext.get(), id, req));
    }

    /** 当前剧场的最新投票（游客可看）。 */
    @GetMapping("/theaters/{id}/polls/active")
    public ApiResult<PollVO> activePoll(@PathVariable Long id) {
        return ApiResult.ok(pollService.active(id, UserContext.get()));
    }

    /** 观众投票（一人一票）。 */
    @PostMapping("/polls/{id}/vote")
    public ApiResult<PollVO> votePoll(@PathVariable Long id, @RequestBody PollVoteReq req) {
        return ApiResult.ok(pollService.vote(UserContext.get(), id, req.getOptionIdx()));
    }

    // ==================== 多剧组 PK ====================

    /** 发起擂台：两部公开直播的剧场同场 PK。 */
    @PostMapping("/pk")
    public ApiResult<PkVO> createPk(@RequestBody PkCreateReq req) {
        return ApiResult.ok(pkService.create(UserContext.get(), req));
    }

    /** 擂台列表（游客可看）。 */
    @GetMapping("/pk/list")
    public ApiResult<List<PkVO>> pkList() {
        return ApiResult.ok(pkService.list(UserContext.get()));
    }

    /** 擂台详情（游客可看）。 */
    @GetMapping("/pk/{id}")
    public ApiResult<PkVO> pkDetail(@PathVariable Long id) {
        return ApiResult.ok(pkService.detail(id, UserContext.get()));
    }

    /** 观众给剧组助威（一人一票）。 */
    @PostMapping("/pk/{id}/vote")
    public ApiResult<PkVO> votePk(@PathVariable Long id, @RequestBody PkVoteReq req) {
        return ApiResult.ok(pkService.vote(UserContext.get(), id, req.getSide()));
    }
}
