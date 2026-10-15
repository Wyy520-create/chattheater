package com.chattheater.persona.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chattheater.common.ApiResult;
import com.chattheater.persona.dto.PersonaDtos.SaveReq;
import com.chattheater.persona.dto.PersonaDtos.TryChatReq;
import com.chattheater.persona.dto.PersonaDtos.TryChatResp;
import com.chattheater.persona.dto.PersonaVO;
import com.chattheater.persona.service.PersonaService;
import com.chattheater.security.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/personas")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    /** 人设市场（游客可浏览）。 */
    @GetMapping
    public ApiResult<Page<PersonaVO>> market(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "12") int size,
                                              @RequestParam(required = false) String archetype,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "hot") String sort) {
        return ApiResult.ok(personaService.market(page, size, archetype, keyword, sort));
    }

    @GetMapping("/archetypes")
    public ApiResult<List<String>> archetypes() {
        return ApiResult.ok(PersonaService.ARCHETYPES);
    }

    @GetMapping("/mine")
    public ApiResult<List<PersonaVO>> mine() {
        return ApiResult.ok(personaService.mine(UserContext.get()));
    }

    @GetMapping("/{id}")
    public ApiResult<PersonaVO> detail(@PathVariable Long id) {
        return ApiResult.ok(personaService.detail(id));
    }

    @PostMapping
    public ApiResult<PersonaVO> create(@Valid @RequestBody SaveReq req) {
        return ApiResult.ok(personaService.create(UserContext.get(), req));
    }

    @PutMapping("/{id}")
    public ApiResult<PersonaVO> update(@PathVariable Long id, @Valid @RequestBody SaveReq req) {
        return ApiResult.ok(personaService.update(UserContext.get(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        personaService.delete(UserContext.get(), id);
        return ApiResult.ok();
    }

    /** 点赞/取消点赞，返回点赞后的状态（true=已点赞）。 */
    @PostMapping("/{id}/like")
    public ApiResult<Boolean> toggleLike(@PathVariable Long id) {
        return ApiResult.ok(personaService.toggleLike(UserContext.get(), id));
    }

    /** 试聊（单轮，不落库）。 */
    @PostMapping("/{id}/try-chat")
    public ApiResult<TryChatResp> tryChat(@PathVariable Long id, @Valid @RequestBody TryChatReq req) {
        return ApiResult.ok(personaService.tryChat(UserContext.get(), id, req.getMessage()));
    }
}
