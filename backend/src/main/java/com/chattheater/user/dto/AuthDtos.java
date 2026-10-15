package com.chattheater.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDtos {

    @Data
    public static class RegisterReq {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 20, message = "用户名长度须为 4~20 位")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度须为 6~32 位")
        private String password;

        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称最长 20 字")
        private String nickname;
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class UpdateProfileReq {
        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称最长 20 字")
        private String nickname;

        @Size(max = 16, message = "头像请使用单个 emoji")
        private String avatar;
    }

    public record LoginResp(String token, UserVO user) {
    }
}
