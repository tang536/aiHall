package com.gxu.aihall.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gxu.aihall.entity.PrivateMessage;
import com.gxu.aihall.service.ChatSessionRegistry;
import com.gxu.aihall.service.PrivateMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 私聊 WebSocket 处理器（原生 WebSocket，不使用 STOMP）。
 * 连接：/ws/chat?token=xxx —— 握手阶段由 WebSocketConfig 的拦截器完成鉴权，
 * 用户 id 写入 session attributes。
 * 客户端可发两种帧：
 *  - {"type":"PING"}                      心跳保活
 *  - {"type":"SEND","toUserId":1,...}     直接经 WebSocket 发送消息（与 REST 走同一套落库逻辑）
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USER_ID = "userId";

    private final ChatSessionRegistry sessionRegistry;
    private final PrivateMessageService messageService;
    private final ChatBroadcaster broadcaster;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(ChatSessionRegistry sessionRegistry,
                                PrivateMessageService messageService,
                                ChatBroadcaster broadcaster) {
        this.sessionRegistry = sessionRegistry;
        this.messageService = messageService;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userIdOf(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        sessionRegistry.register(userId, session);
        // 集群模式下本实例从这一刻起订阅该用户的广播频道；
        // 单机模式内部直接跳过（不依赖 Redis）
        broadcaster.onUserConnected(userId);
        send(session, Map.of("type", "CONNECTED", "userId", userId));
        log.debug("WebSocket 已连接 userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = userIdOf(session);
        if (userId == null) return;

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            send(session, Map.of("type", "ERROR", "message", "消息格式不正确"));
            return;
        }

        String type = payload.get("type") == null ? "SEND" : String.valueOf(payload.get("type"));
        if ("PING".equalsIgnoreCase(type)) {
            send(session, Map.of("type", "PONG"));
            return;
        }

        try {
            Long toUserId = toLong(payload.get("toUserId"));
            String content = payload.get("content") == null ? null : String.valueOf(payload.get("content"));
            String msgType = payload.get("msgType") == null ? "TEXT" : String.valueOf(payload.get("msgType"));
            String refType = payload.get("refType") == null ? null : String.valueOf(payload.get("refType"));
            Long refId = toLong(payload.get("refId"));

            PrivateMessage saved = messageService.send(userId, toUserId, content, msgType, refType, refId);

            // 回执给发送方：携带已落库的消息（含 id 与时间），前端据此渲染自己的气泡
            Map<String, Object> ack = new HashMap<>();
            ack.put("type", "SENT");
            ack.put("message", messageService.toDTO(saved));
            send(session, ack);
        } catch (Exception e) {
            // 发送失败（参数校验、余额不足、会话已失效等）只回复错误，不中断连接
            send(session, Map.of("type", "ERROR",
                    "message", e.getMessage() == null ? "发送失败" : e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
            // 最后一个连接断开才退订（onUserDisconnected 内部会再确认一次在线状态：
            // 连接被新连接顶替时旧连接的关闭回调也会走到这里，那时用户其实还在线）
            broadcaster.onUserDisconnected(userId);
            log.debug("WebSocket 已断开 userId={} status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket 传输异常: {}", exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Long userIdOf(WebSocketSession session) {
        Object v = session.getAttributes().get(ATTR_USER_ID);
        if (v instanceof Long) return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return null;
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws Exception {
        if (session == null || !session.isOpen()) return;
        String json = objectMapper.writeValueAsString(payload);
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    private Long toLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).longValue();
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
