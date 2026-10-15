package com.chattheater.me.controller;

import com.chattheater.common.ApiResult;
import com.chattheater.me.dto.MeDtos.OverviewVO;
import com.chattheater.me.service.MeService;
import com.chattheater.plaza.dto.PlazaDtos.PlazaCardVO;
import com.chattheater.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/overview")
    public ApiResult<OverviewVO> overview() {
        return ApiResult.ok(meService.overview(UserContext.get()));
    }

    @GetMapping("/favorites")
    public ApiResult<List<PlazaCardVO>> favorites() {
        return ApiResult.ok(meService.favorites(UserContext.get()));
    }
}
