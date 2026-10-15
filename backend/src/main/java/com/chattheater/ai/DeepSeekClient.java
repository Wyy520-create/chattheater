package com.chattheater.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * DeepSeek 客户端（OpenAI 兼容协议）。
 * 流式：POST /v1/chat/completions {stream:true}，逐行解析 data: {...delta...}。
 * 上游异常统一抛 IllegalStateException（消息含 HTTP 状态与上游片段），由网关降级处理。
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-seconds}")
    private int timeoutSeconds;

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** 流式对话：逐 delta 回调 onDelta，返回完整文本。 */
    public String streamChat(List<LlmMessage> messages, Consumer<String> onDelta) {
        ObjectNode body = buildBody(messages, true);
        HttpRequest request = buildRequest(body);
        long t0 = System.currentTimeMillis();
        try {
            HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("LLM 上游错误 HTTP " + resp.statusCode() + ": " + readSnippet(resp.body()));
            }
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    JsonNode delta = om.readTree(payload)
                            .path("choices").path(0).path("delta").path("content");
                    if (delta.isTextual()) {
                        String s = delta.asText();
                        full.append(s);
                        if (onDelta != null) {
                            onDelta.accept(s);
                        }
                    }
                }
            }
            log.debug("[LLM] stream ok, {} chars, {} ms", full.length(), System.currentTimeMillis() - t0);
            return full.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("LLM 上游连接失败: " + e.getMessage(), e);
        }
    }

    /** 非流式单轮（试聊）。 */
    public String chat(List<LlmMessage> messages) {
        ObjectNode body = buildBody(messages, false);
        HttpRequest request = buildRequest(body);
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("LLM 上游错误 HTTP " + resp.statusCode() + ": "
                        + abbreviate(resp.body(), 200));
            }
            JsonNode content = om.readTree(resp.body())
                    .path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new IllegalStateException("LLM 返回格式异常: " + abbreviate(resp.body(), 200));
            }
            return content.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("LLM 上游连接失败: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildBody(List<LlmMessage> messages, boolean stream) {
        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);
        body.put("temperature", 0.9);
        body.put("max_tokens", 120);
        ArrayNode arr = body.putArray("messages");
        for (LlmMessage m : messages) {
            ObjectNode node = arr.addObject();
            node.put("role", m.role());
            node.put("content", m.content());
        }
        return body;
    }

    private HttpRequest buildRequest(ObjectNode body) {
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            om.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("构建 LLM 请求失败: " + e.getMessage(), e);
        }
    }

    private String readSnippet(InputStream body) {
        try (body) {
            byte[] buf = body.readNBytes(400);
            return abbreviate(new String(buf, StandardCharsets.UTF_8), 200);
        } catch (Exception e) {
            return "(无法读取上游响应)";
        }
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
