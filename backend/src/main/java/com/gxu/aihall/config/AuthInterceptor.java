package com.gxu.aihall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录态拦截器：
 * 1. /api/admin/** 必须携带有效且属于管理员的活跃 token（后端强制鉴权）；
 * 2. 其余 /api/** 若携带已被新登录顶下的旧 token，立即返回 401 提示被下线。
 * 公共接口（登录/验证码/统计/通知/地图等）不受影响。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String KICKED_MESSAGE = "账号已在其他设备登录，您已被迫下线，请重新登录";
    private static final String NOT_LOGIN_MESSAGE = "未登录或登录已失效，请重新登录";

    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        String rawToken = request.getHeader("Authorization");
        String token = (rawToken != null && rawToken.startsWith("Bearer "))
                ? rawToken.substring(7).trim() : null;

        // 管理端接口：必须登录且为管理员
        if (uri.startsWith("/api/admin/")) {
            String status = authService.getTokenStatus(token);
            if ("KICKED".equals(status)) {
                writeJson(response, 401, KICKED_MESSAGE);
                return false;
            }
            if (!"VALID".equals(status)) {
                writeJson(response, 401, NOT_LOGIN_MESSAGE);
                return false;
            }
            User user = authService.getUserByToken(token);
            if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
                writeJson(response, 403, "无权限访问");
                return false;
            }
            return true;
        }

        // 其他接口：携带的 token 若已被顶下线，统一返回 401 提示
        if (token != null && "KICKED".equals(authService.getTokenStatus(token))) {
            writeJson(response, 401, KICKED_MESSAGE);
            return false;
        }

        return true;
    }

    private void writeJson(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
