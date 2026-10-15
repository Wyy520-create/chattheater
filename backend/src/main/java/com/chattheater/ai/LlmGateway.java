package com.chattheater.ai;

import com.chattheater.ai.entity.LlmUsage;
import com.chattheater.ai.mapper.LlmUsageMapper;
import com.chattheater.persona.entity.Persona;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 网关：Real（DeepSeek）⇄ Mock（离线演员）双通道。
 * - key 为空 → 纯 Mock
 * - key 存在 → Real；Real 异常自动降级 Mock，并把上游错误带回给引擎提示观众
 * - 每次调用记 llm_usage（token 按字符估算：中文约 2 字符 ≈ 1 token）
 */
@Slf4j
@Service
public class LlmGateway {

    /** token 估算系数：约 2 个字符 1 个 token。 */
    private static final int CHARS_PER_TOKEN = 2;

    private final DeepSeekClient real;
    private final MockLlmClient mock;
    private final LlmUsageMapper usageMapper;

    public LlmGateway(DeepSeekClient real, MockLlmClient mock, LlmUsageMapper usageMapper) {
        this.real = real;
        this.mock = mock;
        this.usageMapper = usageMapper;
    }

    public record Reply(String text, boolean usedMock, String upstreamError) {
    }

    public boolean mockMode() {
        return !real.available();
    }

    /** 群聊发言（流式）。 */
    public Reply chat(ChatContext ctx, Long groupId, Consumer<String> onDelta) {
        List<LlmMessage> messages = PromptBuilder.build(ctx);
        int promptChars = PromptBuilder.approxPromptChars(messages);
        long t0 = System.currentTimeMillis();

        if (real.available()) {
            try {
                String text = real.streamChat(messages, onDelta);
                recordUsage(groupId, ctx.persona().getId(), false, true,
                        promptChars, text.length(), (int) (System.currentTimeMillis() - t0));
                return new Reply(text, false, null);
            } catch (Exception e) {
                log.warn("[LLM] Real 通道失败，降级 Mock: {}", e.getMessage());
                recordUsage(groupId, ctx.persona().getId(), false, false,
                        promptChars, 0, (int) (System.currentTimeMillis() - t0));
                String text = mock.streamReply(ctx, onDelta);
                return new Reply(text, true, e.getMessage());
            }
        }
        String text = mock.streamReply(ctx, onDelta);
        recordUsage(groupId, ctx.persona().getId(), true, true,
                promptChars, text.length(), (int) (System.currentTimeMillis() - t0));
        return new Reply(text, true, null);
    }

    /** 人设工坊试聊（非流式）。 */
    public Reply tryTalk(Persona persona, String userMessage) {
        List<LlmMessage> messages = PromptBuilder.buildTryTalk(persona, userMessage);
        if (real.available()) {
            try {
                String text = real.chat(messages);
                return new Reply(text, false, null);
            } catch (Exception e) {
                log.warn("[LLM] tryTalk Real 失败，降级 Mock: {}", e.getMessage());
                return new Reply(mock.replyTryTalk(persona, userMessage), true, e.getMessage());
            }
        }
        return new Reply(mock.replyTryTalk(persona, userMessage), true, null);
    }

    private void recordUsage(Long groupId, Long personaId, boolean isMock, boolean ok,
                             int promptChars, int completionChars, int latencyMs) {
        try {
            LlmUsage usage = new LlmUsage();
            usage.setGroupId(groupId);
            usage.setPersonaId(personaId);
            usage.setModel(isMock ? "offline-actor" : real.model());
            usage.setMock(isMock ? 1 : 0);
            usage.setPromptTokens(promptChars / CHARS_PER_TOKEN);
            usage.setCompletionTokens(completionChars / CHARS_PER_TOKEN);
            usage.setLatencyMs(latencyMs);
            usage.setOk(ok ? 1 : 0);
            usageMapper.insert(usage);
        } catch (Exception e) {
            log.warn("[LLM] usage 记录失败: {}", e.getMessage());
        }
    }
}
