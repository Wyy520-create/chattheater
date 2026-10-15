package com.chattheater.persona.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class PersonaDtos {

    @Data
    public static class SaveReq {
        @NotBlank(message = "人设名字不能为空")
        @Size(max = 20, message = "名字最长 20 字")
        private String name;

        @Size(max = 16, message = "头像请使用单个 emoji")
        private String avatar;

        @NotBlank(message = "原型不能为空")
        private String archetype;

        @Size(max = 100, message = "一句话人设最长 100 字")
        private String tagline;

        @Size(max = 500, message = "性格描述最长 500 字")
        private String traits;

        @Size(max = 500, message = "说话风格最长 500 字")
        private String speakingStyle;

        @Size(max = 1000, message = "背景故事最长 1000 字")
        private String background;

        @Size(max = 500, message = "雷点最长 500 字")
        private String taboo;
    }

    @Data
    public static class TryChatReq {
        @NotBlank(message = "消息不能为空")
        @Size(max = 200, message = "单条消息最长 200 字")
        private String message;
    }

    public record TryChatResp(String reply, boolean mock, String upstreamError) {
    }
}
