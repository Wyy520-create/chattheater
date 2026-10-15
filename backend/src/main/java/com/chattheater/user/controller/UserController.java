package com.chattheater.user.controller;

import com.chattheater.common.ApiResult;
import com.chattheater.security.UserContext;
import com.chattheater.user.dto.AuthDtos.UpdateProfileReq;
import com.chattheater.user.dto.UserVO;
import com.chattheater.user.entity.User;
import com.chattheater.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResult<UserVO> me() {
        return ApiResult.ok(userService.me(UserContext.get()));
    }

    @PutMapping("/me")
    public ApiResult<UserVO> update(@Valid @RequestBody UpdateProfileReq req) {
        return ApiResult.ok(userService.updateProfile(UserContext.get(), req.getNickname(), req.getAvatar()));
    }
}
