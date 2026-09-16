package com.gxu.aihall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪：给每个请求分配一个 traceId，写入 MDC 与响应头 {@code X-Trace-Id}。
 * <p>日志格式里已经带了 {@code [%X{traceId}]}，排查问题时用响应头里的 traceId
 * 就能把一次请求跨多条日志、甚至跨服务串起来。客户端传了合法的 X-Trace-Id 就沿用，
 * 便于前后端联调对齐同一次调用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = normalize(request.getHeader(HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程会被复用，必须清理，否则下个请求会带着上个请求的 traceId
            MDC.remove(MDC_KEY);
        }
    }

    /** 只接受长度合理且字符集安全的传入值，避免日志注入 */
    private String normalize(String incoming) {
        if (incoming != null && !incoming.isBlank() && incoming.length() <= MAX_LENGTH
                && incoming.matches("[A-Za-z0-9_-]+")) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
