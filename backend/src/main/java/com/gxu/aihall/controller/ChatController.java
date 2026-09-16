package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.ChatRequest;
import com.gxu.aihall.dto.ChatResponse;
import com.gxu.aihall.entity.ChatMessage;
import com.gxu.aihall.service.ChatService;
import com.gxu.aihall.service.OllamaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final OllamaService ollamaService;
    private final ExecutorService ssePool = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService, OllamaService ollamaService) {
        this.chatService = chatService;
        this.ollamaService = ollamaService;
    }

    @PostMapping("/send")
    public Result<ChatResponse> send(@RequestBody ChatRequest request) {
        return Result.success(chatService.chat(request));
    }

    /**
     * 流式问答（SSE）：逐段推送 delta，最后推送 done 事件
     */
    @PostMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        ssePool.submit(() -> {
            try {
                // 立即推送"已连接"事件，让前端知道模型正在思考
                emitter.send(SseEmitter.event().name("connected").data(Map.of("message", "AI正在思考，请稍候...")));
                ChatResponse resp = chatService.streamChat(request, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(Map.of("content", delta)));
                    } catch (Exception ignored) {
                    }
                });
                emitter.send(SseEmitter.event().name("done").data(resp));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "AI 服务异常：" + e.getMessage())));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/history/{sessionId}")
    public Result<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        return Result.success(chatService.getSessionHistory(sessionId));
    }

    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestBody Map<String, Object> body) {
        Long messageId = Long.valueOf(body.get("messageId").toString());
        Integer feedback = (Integer) body.get("feedback");
        chatService.feedback(messageId, feedback);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        boolean available = ollamaService.isAvailable();
        String model = ollamaService.getModelName();
        return Result.success(Map.of(
                "ollamaAvailable", available,
                "model", model,
                "message", available ? "AI服务正常（" + model + "）" : "Ollama未启动，请先启动 Ollama 并拉取 " + model + " 模型"
        ));
    }
}
