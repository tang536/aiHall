package com.gxu.aihall.config;

import com.gxu.aihall.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 令牌滑动续期拦截器。
 * <p>令牌剩余有效期低于 {@code app.jwt.renew-before-minutes}（默认 120 分钟）时，
 * 服务端用<b>同一个 jti</b> 换发一枚新令牌，通过响应头 {@value #NEW_TOKEN_HEADER} 回传。
 * 前端拿到后静默替换本地令牌 —— 用户只要还在用，就不会遇到「24 小时后被踢出去」。
 * <p>为什么回传响应头而不是开一个 {@code POST /auth/refresh} 接口：
 * 刷新接口需要前端自己判断时机（定时器、或先吃过一次 401 再补），多一轮失败重试逻辑；
 * 由服务端在正常请求上顺带下发，前端只多一行「看到这个头就换掉本地令牌」，
 * 没有额外的失败路径，也不会出现「过期那一刻正好没有请求」的窗口。
 */
@Slf4j
@Component
public class TokenRefreshInterceptor implements HandlerInterceptor {

    public static final String NEW_TOKEN_HEADER = "X-New-Token";
    /** 新令牌的绝对过期时间（ISO-8601），前端可用于提前提醒或调试 */
    public static final String EXPIRES_AT_HEADER = "X-Token-Expires-At";

    private final AuthService authService;

    public TokenRefreshInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String raw = request.getHeader("Authorization");
        if (raw == null || raw.isBlank()) return true;
        try {
            String renewed = authService.maybeRenew(raw);
            if (renewed != null) {
                response.setHeader(NEW_TOKEN_HEADER, renewed);
                log.debug("令牌已滑动续期：新令牌已通过 {} 响应头下发", NEW_TOKEN_HEADER);
            }
        } catch (Exception e) {
            // 续期只是顺带做的事，失败绝不能影响本次请求
            log.warn("令牌续期失败（不影响本次请求）: {}", e.getMessage());
        }
        return true;
    }
}
