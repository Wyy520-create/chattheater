package com.chattheater.user.controller;

import com.chattheater.common.ApiResult;
import com.chattheater.user.dto.AuthDtos.LoginReq;
import com.chattheater.user.dto.AuthDtos.LoginResp;
import com.chattheater.user.dto.AuthDtos.RegisterReq;
import com.chattheater.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResult<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        return ApiResult.ok(userService.register(req));
    }

    @PostMapping("/login")
    public ApiResult<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResult.ok(userService.login(req));
    }
}
