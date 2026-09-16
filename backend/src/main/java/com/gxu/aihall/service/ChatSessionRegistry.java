package com.gxu.aihall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 在线会话注册表：userId → WebSocketSession（单用户单连接）。
 * 注意：WebSocketSession#sendMessage 不是线程安全的，因此发送时对 session 加锁；
 * 同一用户重复连接时，新连接顶掉旧连接并关闭旧的。
 */
@Slf4j
@Component
public class ChatSessionRegistry {

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 注册连接（并在替换时关闭旧连接） */
    public void register(Long userId, WebSocketSession session) {
        if (userId == null) return;
        WebSocketSession old = sessions.put(userId, session);
        if (old != null && old.isOpen() && !old.getId().equals(session.getId())) {
            try {
                old.close();
            } catch (Exception e) {
                log.debug("关闭旧的 WebSocket 连接失败: {}", e.getMessage());
            }
        }
    }

    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null) return;
        sessions.computeIfPresent(userId, (k, v) -> v.getId().equals(session.getId()) ? null : v);
    }

    public boolean isOnline(Long userId) {
        WebSocketSession s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    /**
     * 向指定用户推送消息；对方不在线时静默跳过（消息已落库，对方上线后拉取即可）。
     * @return 是否成功推送
     */
    public boolean sendTo(Long userId, Object payload) {
        if (userId == null || payload == null) return false;
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) return false;
        try {
            String json = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
            // sendMessage 非线程安全：同一连接并发发送会抛 IllegalStateException
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (Exception e) {
            log.warn("WebSocket 推送失败 userId={}: {}", userId, e.getMessage());
            return false;
        }
    }
}
