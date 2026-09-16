package com.gxu.aihall.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gxu.aihall.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ollama 大模型服务
 * 支持本地模型（qwen2.5 / deepseek-r1 等），提供非流式与流式(SSE)两种调用
 */
@Slf4j
@Service
public class OllamaService {

    private final OllamaConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient streamClient;

    public OllamaService(OllamaConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.streamClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 调用 Ollama Chat API（非流式）
     */
    public String chat(String systemPrompt, String userMessage, List<String> history) {
        try {
            String url = config.getBaseUrl() + "/api/chat";
            String payload = buildPayload(systemPrompt, userMessage, history, false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            log.info("调用 Ollama(非流式): model={}", config.getModel());
            long start = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Ollama 响应耗时: {}ms", System.currentTimeMillis() - start);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("message").path("content").asText("");
                return extractAnswer(content);
            }
            return "抱歉，AI服务暂时不可用，请稍后重试。";
        } catch (Exception e) {
            log.error("Ollama 调用失败: {}", e.getMessage());
            return "AI服务连接失败，请确保 Ollama 已启动并已拉取 " + config.getModel() + " 模型。错误信息: " + e.getMessage();
        }
    }

    /**
     * 流式调用 Ollama Chat API，每生成一段文本就回调 onDelta，最终返回完整回答。
     * 用于 SSE 逐字输出，显著降低用户等待体感。
     */
    public String streamChat(String systemPrompt, String userMessage, List<String> history,
                             Consumer<String> onDelta) {
        StringBuilder full = new StringBuilder();
        int sentLen = 0; // 已推送给前端的"可见答案"长度（剥离 r1 思维链后的增量计算）
        try {
            String payload = buildPayload(systemPrompt, userMessage, history, true);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(config.getTimeout() == null ? 120 : config.getTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            log.info("调用 Ollama(流式): model={}", config.getModel());
            long start = System.currentTimeMillis();

            java.net.http.HttpResponse<java.io.InputStream> resp =
                    streamClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 200) {
                String errBody = "";
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                    errBody = errReader.lines().collect(java.util.stream.Collectors.joining("\n"));
                } catch (Exception ignored) {}
                // 不打印请求 payload 与响应体正文：payload 里是用户的提问和检索上下文，
                // 属于用户私有内容。这里只留长度与截断后的服务端错误信息，够定位问题又不泄露内容。
                log.error("Ollama 流式返回状态码 {}，payload 长度 {}，响应体前 300 字: {}",
                        resp.statusCode(),
                        payload.length(),
                        errBody.length() > 300 ? errBody.substring(0, 300) : errBody);
                throw new RuntimeException("Ollama 返回状态码 " + resp.statusCode() + ": " + errBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode node = objectMapper.readTree(line);
                    String piece = node.path("message").path("content").asText("");
                    if (!piece.isEmpty()) {
                        full.append(piece);
                        // 仅推送"可见答案"增量：deepseek-r1 的思维链(think 块)不推给前端
                        if (onDelta != null) {
                            String visible = visibleAnswer(full.toString());
                            if (visible.length() > sentLen) {
                                onDelta.accept(visible.substring(sentLen));
                                sentLen = visible.length();
                            }
                        }
                    }
                    if (node.path("done").asBoolean(false)) break;
                }
            }
            log.info("Ollama 流式生成完成, 耗时: {}ms", System.currentTimeMillis() - start);
            String finalAnswer = extractAnswer(full.toString());
            // 兜底：若从未推送过可见内容（思维链被截断等），推一次最终答案
            if (onDelta != null && sentLen == 0 && !finalAnswer.isEmpty()) {
                onDelta.accept(finalAnswer);
            }
            return finalAnswer;
        } catch (Exception e) {
            log.error("Ollama 流式调用失败: {}", e.getMessage());
            throw new RuntimeException("AI 流式生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式过程中计算当前应对前端可见的文本：
     * - 非推理模型（无 think 标签）：原样可见
     * - deepseek-r1：思维链闭合前返回空（前端显示"思考中"），闭合后只返回最终答案部分
     * - 思维链未闭合（被截断）：返回 <think> 之前的内容（如有），否则保持空
     */
    private String visibleAnswer(String raw) {
        int start = raw.indexOf("<think>");
        if (start < 0) return raw;
        int end = raw.indexOf("</think>", start);
        if (end < 0) {
            return start > 0 ? raw.substring(0, start) : "";
        }
        return raw.substring(end + "</think>".length());
    }

    /**
     * 构建请求体（流式/非流式共用）
     */
    private String buildPayload(String systemPrompt, String userMessage, List<String> history, boolean stream)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());
        body.put("stream", stream);
        // 模型常驻，避免每次冷加载
        body.put("keep_alive", config.getKeepAlive() == null ? "30m" : config.getKeepAlive());

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt == null ? "" : systemPrompt);
        messages.add(sysMsg);

        if (history != null) {
            for (int i = 0; i < history.size(); i += 2) {
                if (i < history.size() && history.get(i) != null) {
                    ObjectNode uMsg = objectMapper.createObjectNode();
                    uMsg.put("role", "user");
                    uMsg.put("content", history.get(i));
                    messages.add(uMsg);
                }
                if (i + 1 < history.size() && history.get(i + 1) != null) {
                    ObjectNode aMsg = objectMapper.createObjectNode();
                    aMsg.put("role", "assistant");
                    aMsg.put("content", history.get(i + 1));
                    messages.add(aMsg);
                }
            }
        }

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage == null ? "" : userMessage);
        messages.add(userMsg);
        body.set("messages", messages);

        ObjectNode options = objectMapper.createObjectNode();
        options.put("temperature", config.getTemperature());
        options.put("top_p", config.getTopP());
        if (config.getNumCtx() != null) options.put("num_ctx", config.getNumCtx());
        if (config.getNumPredict() != null) options.put("num_predict", config.getNumPredict());
        body.set("options", options);

        return objectMapper.writeValueAsString(body);
    }

    /**
     * 从 deepseek-r1 输出中去除 think 思维链标签，只保留最终回答。
     * 兼容闭合标签 <think>...</think> 与未闭合（被截断）的 <think>...
     * 去除后若为空，返回友好提示而非原始思维链内容。
     */
    private String extractAnswer(String content) {
        if (content == null) return "";
        // 去掉闭合的 <think>...</think>
        String result = content.replaceAll("(?s)<think>.*?</think>", "");
        // 去掉未闭合的 <think>...（到末尾）
        int openIdx = result.indexOf("<think>");
        if (openIdx >= 0) result = result.substring(0, openIdx);
        result = result.trim();
        if (result.isEmpty()) {
            // 只记长度不记正文：模型原始输出可能整段复述用户提问
            log.warn("Ollama 返回去除思维链后为空（原始输出长度 {}）", content.length());
            return "抱歉，我暂时无法回答这个问题，请换一种方式提问或稍后再试。";
        }
        return result;
    }

    /**
     * 检查 Ollama 服务是否可用
     */
    public boolean isAvailable() {
        try {
            String url = config.getBaseUrl() + "/api/tags";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 当前配置的模型名
     */
    public String getModelName() {
        return config.getModel();
    }

    /**
     * 简单文本生成（不带历史）
     */
    public String generate(String prompt) {
        return chat("你是一个 helpful 的助手。", prompt, new ArrayList<>());
    }
}
