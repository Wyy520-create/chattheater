package com.chattheater.ai;

import com.chattheater.persona.entity.Persona;
import com.chattheater.theater.entity.Message;

import java.util.List;

/**
 * 一次群聊发言的完整上下文（供 PromptBuilder 与 Mock 使用）。
 *
 * @param persona        本轮发言的人设
 * @param scenarioBackdrop 舞台设定快照
 * @param directorNote   导演总指令（可空）
 * @param history        最近群聊记录（时间正序）
 * @param directives     待注入的导演突发指令/事件卡
 */
public record ChatContext(Persona persona,
                          String scenarioBackdrop,
                          String directorNote,
                          List<Message> history,
                          List<String> directives) {
}
