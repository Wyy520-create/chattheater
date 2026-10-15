package com.chattheater.ai;

import com.chattheater.persona.entity.Persona;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 离线演员（Mock）客户端：模板生成 + 逐字模拟流式输出。
 */
@Component
public class MockLlmClient {

    /** 群聊流式回复。 */
    public String streamReply(ChatContext ctx, java.util.function.Consumer<String> onDelta) {
        String full = MockReplier.generate(ctx);
        simulateStream(full, onDelta);
        return full;
    }

    /** 试聊单轮。 */
    public String replyTryTalk(Persona persona, String userMessage) {
        return MockReplier.generateTryTalk(persona, userMessage);
    }

    private void simulateStream(String full, java.util.function.Consumer<String> onDelta) {
        if (onDelta == null) {
            return;
        }
        try {
            int i = 0;
            while (i < full.length()) {
                int step = ThreadLocalRandom.current().nextInt(1, 4);
                int end = Math.min(full.length(), i + step);
                onDelta.accept(full.substring(i, end));
                i = end;
                Thread.sleep(ThreadLocalRandom.current().nextInt(40, 90));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
