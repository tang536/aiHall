package com.gxu.aihall.config;

import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.ws.ChatWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 配置：注册 /ws/chat 用于私聊实时消息。
 * 鉴权说明：/ws/** 不在 MVC 拦截器（/api/**）范围内，因此握手请求必须
 * 在此处自行校验 token（通过 ?token=xxx 传入），校验失败直接拒绝握手，
 * 前端收到连接失败后会降级为轮询未读数。
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final AuthService authService;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, AuthService authService) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.authService = authService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(new TokenHandshakeInterceptor(authService))
                .setAllowedOriginPatterns("*");
    }

    /** 握手拦截器：从查询参数 token 解析登录用户 */
    static class TokenHandshakeInterceptor implements HandshakeInterceptor {

        private final AuthService authService;

        TokenHandshakeInterceptor(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String token = null;
            if (request instanceof ServletServerHttpRequest servletRequest) {
                token = servletRequest.getServletRequest().getParameter("token");
            }
            if (token == null || token.isBlank()) {
                // 兼容以 Authorization 头传入的场景
                String auth = request.getHeaders().getFirst("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7).trim();
                }
            }
            if (token == null || token.isBlank()) {
                reject(response, "缺少登录凭证");
                return false;
            }
            User user = authService.getUserByToken(token.replace("Bearer ", "").trim());
            if (user == null) {
                reject(response, "登录已失效");
                return false;
            }
            attributes.put(ChatWebSocketHandler.ATTR_USER_ID, user.getId());
            return true;
        }

        /** 明确返回 403，便于前端区分「鉴权失败」与「网络异常」 */
        private void reject(ServerHttpResponse response, String reason) {
            log.debug("WebSocket 握手被拒绝：{}", reason);
            response.setStatusCode(HttpStatus.FORBIDDEN);
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 无需处理
        }
    }
}
