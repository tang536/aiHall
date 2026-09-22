package com.gxu.aihall.service;

import com.gxu.aihall.dto.ChatRequest;
import com.gxu.aihall.dto.ChatResponse;
import com.gxu.aihall.entity.ChatMessage;
import com.gxu.aihall.entity.KnowledgeChunk;
import com.gxu.aihall.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 问答服务（RAG + Ollama）
 */
@Slf4j
@Service
public class ChatService {

    private final OllamaService ollamaService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatMessageRepository messageRepository;

    public ChatService(OllamaService ollamaService,
                       KnowledgeBaseService knowledgeBaseService,
                       ChatMessageRepository messageRepository) {
        this.ollamaService = ollamaService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.messageRepository = messageRepository;
    }

    /**
     * 处理用户提问
     */
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 1. 标准 RAG 检索：文档分块 → TF-IDF 向量化 → 余弦相似度
        List<KnowledgeChunk> relevantChunks = knowledgeBaseService.retrieveChunks(request.getMessage(), 5);
        String context = knowledgeBaseService.buildContextFromChunks(relevantChunks);
        List<String> sources = knowledgeBaseService.getSourcesFromChunks(relevantChunks);

        // 2. 构建系统提示词
        String systemPrompt = buildSystemPrompt(context);

        // 3. 获取历史对话（最近5轮）
        List<String> history = getHistory(sessionId);

        // 4. 调用 Ollama
        String answer = ollamaService.chat(systemPrompt, request.getMessage(), history);

        long responseTime = System.currentTimeMillis() - startTime;

        // AI 命中日志：一次问答完整链路
        log.info("[AI问答] session={}, userId={}, 问题=\"{}\", RAG命中{}个分块[{}], 模型耗时{}ms, 回答{}字",
                sessionId, request.getUserId(), abbreviate(request.getMessage(), 100),
                relevantChunks.size(), String.join(";", sources), responseTime, answer.length());

        // 5. 保存对话记录
        saveMessage(sessionId, request.getUserId(), "user", request.getMessage(), null);
        Long assistantMsgId = saveMessage(sessionId, request.getUserId(), "assistant", answer,
                sources.isEmpty() ? null : String.join("|", sources), responseTime);

        // 6. 构建响应
        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setSources(sources);
        response.setResponseTime(responseTime);
        response.setSessionId(sessionId);
        response.setMessageId(assistantMsgId);

        return response;
    }

    /**
     * 流式处理用户提问：边生成边通过 onDelta 推送，生成结束后落库并返回完整结果
     */
    public ChatResponse streamChat(ChatRequest request, java.util.function.Consumer<String> onDelta) {
        long startTime = System.currentTimeMillis();
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 1. 标准 RAG 检索：文档分块 → TF-IDF 向量化 → 余弦相似度
        List<KnowledgeChunk> relevantChunks = knowledgeBaseService.retrieveChunks(request.getMessage(), 5);
        String context = knowledgeBaseService.buildContextFromChunks(relevantChunks);
        List<String> sources = knowledgeBaseService.getSourcesFromChunks(relevantChunks);

        // 2. 系统提示词 + 历史
        String systemPrompt = buildSystemPrompt(context);
        List<String> history = getHistory(sessionId);

        // 3. 先保存用户提问
        saveMessage(sessionId, request.getUserId(), "user", request.getMessage(), null, null);

        // 4. 流式调用 Ollama，逐段回调
        final String sid = sessionId;
        String answer = ollamaService.streamChat(systemPrompt, request.getMessage(), history, delta -> {
            // 思维链标签片段不推给前端（qwen 无，r1 兼容）
            if (onDelta != null) onDelta.accept(delta);
        });

        long responseTime = System.currentTimeMillis() - startTime;

        // AI 命中日志（流式）
        log.info("[AI问答-流式] session={}, userId={}, 问题=\"{}\", RAG命中{}个分块[{}], 模型耗时{}ms, 回答{}字",
                sid, request.getUserId(), abbreviate(request.getMessage(), 100),
                relevantChunks.size(), String.join(";", sources), responseTime, answer.length());

        // 5. 保存 AI 回答
        Long assistantMsgId = saveMessage(sid, request.getUserId(), "assistant", answer,
                sources.isEmpty() ? null : String.join("|", sources), responseTime);

        // 6. 返回完整结果
        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setSources(sources);
        response.setResponseTime(responseTime);
        response.setSessionId(sid);
        response.setMessageId(assistantMsgId);
        return response;
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是广西大学AI学生事务智能办事大厅的智能助手「校园小助手」。\n");
        sb.append("你的职责是帮助学生解答校园事务相关问题，包括奖助学金、请假流程、证明开具、宿舍报修、课表考试、校园地图、失物招领、通知公告等。\n");
        sb.append("回答要求：\n");
        sb.append("1. 基于提供的校园知识库资料回答，确保信息准确\n");
        sb.append("2. 回答简洁明了，分点说明，重点突出\n");
        sb.append("3. 如果知识库中没有相关信息，如实告知，并建议咨询相关部门\n");
        sb.append("4. 语气友好、耐心，使用中文回答\n");
        sb.append("5. 涉及具体流程时，说明所需材料、办理时间和办理地点\n\n");
        if (context != null && !context.isEmpty()) {
            sb.append(context);
        }
        return sb.toString();
    }

    /**
     * 获取历史对话
     */
    private List<String> getHistory(String sessionId) {
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
        List<String> history = new ArrayList<>();
        // 取最近10条（5轮）
        int start = Math.max(0, messages.size() - 10);
        for (int i = start; i < messages.size(); i++) {
            history.add(messages.get(i).getContent());
        }
        return history;
    }

    /**
     * 保存消息
     */
    private Long saveMessage(String sessionId, Long userId, String role, String content, String sources) {
        return saveMessage(sessionId, userId, role, content, sources, null);
    }

    /**
     * 保存消息（带响应耗时），返回保存后的消息ID（失败返回 null）
     */
    private Long saveMessage(String sessionId, Long userId, String role, String content,
                             String sources, Long responseTime) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(sessionId);
            msg.setUserId(userId);
            msg.setRole(role);
            msg.setContent(content);
            msg.setSources(sources);
            msg.setResponseTime(responseTime);
            return messageRepository.save(msg).getId();
        } catch (Exception e) {
            log.error("保存聊天记录失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取会话历史
     */
    public List<ChatMessage> getSessionHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * 反馈
     */
    public void feedback(Long messageId, Integer feedback) {
        messageRepository.findById(messageId).ifPresent(msg -> {
            msg.setFeedback(feedback);
            messageRepository.save(msg);
        });
    }
}
