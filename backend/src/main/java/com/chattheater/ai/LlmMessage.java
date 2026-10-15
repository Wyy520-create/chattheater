package com.chattheater.ai;

/**
 * OpenAI 兼容协议的消息单元。
 */
public record LlmMessage(String role, String content) {
}
