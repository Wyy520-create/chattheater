package com.chattheater.theater.controller;

import com.chattheater.common.ApiResult;
import com.chattheater.security.UserContext;
import com.chattheater.theater.dto.TheaterDtos.CreateReq;
import com.chattheater.theater.dto.TheaterDtos.DirectorReq;
import com.chattheater.theater.dto.TheaterDtos.EndReq;
import com.chattheater.theater.dto.TheaterDtos.MessageVO;
import com.chattheater.theater.dto.TheaterDtos.PostMessageReq;
import com.chattheater.theater.dto.TheaterDtos.TheaterVO;
import com.chattheater.theater.engine.EventCards;
import com.chattheater.theater.service.TheaterService;
import com.chattheater.user.entity.User;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TheaterController {

    private final TheaterService theaterService;

    public TheaterController(TheaterService theaterService) {
        this.theaterService = theaterService;
    }

    @PostMapping("/theaters")
    public ApiResult<TheaterVO> create(@RequestBody CreateReq req) {
        return ApiResult.ok(theaterService.create(UserContext.get(), req));
    }

    @GetMapping("/theaters/mine")
    public ApiResult<List<TheaterVO>> mine() {
        return ApiResult.ok(theaterService.mine(UserContext.get()));
    }

    /** 事件卡目录（导演台渲染用）。 */
    @GetMapping("/theaters/event-cards")
    public ApiResult<List<EventCards.Card>> eventCards() {
        return ApiResult.ok(EventCards.ALL);
    }

    @GetMapping("/theaters/{id}")
    public ApiResult<TheaterVO> detail(@PathVariable Long id) {
        return ApiResult.ok(theaterService.detail(id, UserContext.get()));
    }

    @GetMapping("/theaters/{id}/messages")
    public ApiResult<List<MessageVO>> messages(@PathVariable Long id,
                                               @RequestParam(required = false) Long before,
                                               @RequestParam(required = false) Integer limit) {
        return ApiResult.ok(theaterService.messages(id, UserContext.get(), before, limit));
    }

    @PostMapping("/theaters/{id}/messages")
    public ApiResult<MessageVO> postMessage(@PathVariable Long id, @RequestBody PostMessageReq req) {
        return ApiResult.ok(theaterService.postMessage(UserContext.get(), id, req.getContent()));
    }

    /** SSE 直播流（游客可围观 PUBLIC 剧场；token 走 ?token= 查询参数）。 */
    @GetMapping(value = "/theaters/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        return theaterService.stream(id, UserContext.get());
    }

    @PostMapping("/theaters/{id}/director")
    public ApiResult<Map<String, Object>> director(@PathVariable Long id, @RequestBody DirectorReq req) {
        return ApiResult.ok(theaterService.director(UserContext.get(), id, req));
    }

    @PostMapping("/theaters/{id}/end")
    public ApiResult<Map<String, Object>> end(@PathVariable Long id,
                                              @RequestBody(required = false) EndReq req) {
        String reason = req == null ? null : req.getReason();
        return ApiResult.ok(theaterService.end(UserContext.get(), id, reason));
    }

    @PostMapping("/theaters/{id}/like")
    public ApiResult<Map<String, Object>> like(@PathVariable Long id) {
        boolean liked = theaterService.toggleLike(UserContext.get(), id);
        return ApiResult.ok(Map.of("liked", liked));
    }

    @PostMapping("/theaters/{id}/favorite")
    public ApiResult<Map<String, Object>> favorite(@PathVariable Long id) {
        boolean favorited = theaterService.toggleFavorite(UserContext.get(), id);
        return ApiResult.ok(Map.of("favorited", favorited));
    }

    @PostMapping("/messages/{id}/like")
    public ApiResult<Map<String, Object>> likeMessage(@PathVariable Long id) {
        return ApiResult.ok(theaterService.toggleMessageLike(UserContext.get(), id));
    }
}
