package com.gxu.aihall.audit;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.AuditLog;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuditService;
import com.gxu.aihall.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感操作留痕切面：环绕 {@link Audited} 标注的方法，写 {@code audit_log}。
 * 成功与失败都记：「谁尝试改了谁的余额但被拒绝」和「改成功了」同样是可追溯信息，
 * 所以异常只记录不吞，照常向上抛给全局异常处理器。
 * 为什么放在 Controller 层而不是 Service 层：Service 能被 WebSocket 处理器、
 * 定时任务、脚本调用，那时没有 HTTP 请求上下文，拿不到操作人和 IP。管理员敏感操作
 * 入口都在 Controller（且有 AuthInterceptor 兜底 ADMIN），在这里切最完整。
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;
    private final AuthService authService;

    public AuditAspect(AuditService auditService, AuthService authService) {
        this.auditService = auditService;
        this.authService = authService;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();

        AuditLog entry = new AuditLog();
        entry.setAction(audited.action());
        entry.setTargetType(audited.targetType());
        entry.setCreateTime(java.time.LocalDateTime.now());
        fillRequester(entry);
        entry.setTargetId(findArg(names, args, audited.targetIdParam()));
        try {
            entry.setDetail(buildDetail(names, args, audited));
        } catch (Exception e) {
            // 拼审计摘要失败不该影响主流程，降级为空摘要
            entry.setDetail("");
        }

        try {
            Object ret = pjp.proceed();
            markFromReturn(entry, ret);
            auditService.record(entry);
            return ret;
        } catch (Throwable t) {
            entry.setResult("FAILURE");
            entry.setErrorMsg(t.getClass().getSimpleName() + ": " + t.getMessage());
            auditService.record(entry);
            throw t;
        }
    }

    /**
     * 一部分管理端方法用 {@code Result.error(...)} 表达失败而不是抛异常
     * （例如「不能禁用管理员账号」）。只看「有没有抛异常」会把这类拒绝记成成功，
     * 所以这里再看一眼统一响应体的 code。
     */
    private void markFromReturn(AuditLog entry, Object ret) {
        if (ret instanceof Result<?> result) {
            Integer code = result.getCode();
            if (code != null && code != 200) {
                entry.setResult("FAILURE");
                entry.setErrorMsg("BizException: " + result.getMessage());
                return;
            }
        }
        entry.setResult("SUCCESS");
    }

    /** 从当前请求上下文取操作人与 IP；不在 HTTP 请求里（如测试、内部调用）就留空 */
    private void fillRequester(AuditLog entry) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        HttpServletRequest request = attrs.getRequest();
        entry.setIp(clientIp(request));
        String auth = request.getHeader("Authorization");
        if (auth == null || auth.isBlank()) return;
        try {
            User operator = authService.getUserByBearerToken(auth);
            if (operator != null) {
                entry.setOperatorId(operator.getId());
                entry.setOperatorName(operator.getUsername());
            }
        } catch (Exception e) {
            // 取不到操作人不算错误：写一张没有操作人的记录也比不写强
            log.debug("解析审计操作人失败: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 反向代理链路可能是 "client, proxy1, proxy2"，第一个才是真实来源
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long findArg(String[] names, Object[] args, String paramName) {
        if (names == null || args == null || paramName == null || paramName.isEmpty()) return null;
        for (int i = 0; i < names.length; i++) {
            if (paramName.equals(names[i])) {
                Object v = args[i];
                if (v instanceof Number n) return n.longValue();
                if (v instanceof String s) {
                    try {
                        return Long.valueOf(s);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 拼操作摘要。只取白名单键：路径变量 + {@code includeParams} 明确列出的 body 键。
     * 这样即使用户改了 body 里的其它字段（密码、手机、邮箱），也不会被写进审计日志。
     */
    private String buildDetail(String[] names, Object[] args, Audited audited) {
        List<String> wanted = new ArrayList<>();
        if (audited.targetIdParam() != null && !audited.targetIdParam().isEmpty()) {
            wanted.add(audited.targetIdParam());
        }
        for (String p : audited.includeParams()) {
            if (p != null && !p.isEmpty()) wanted.add(p);
        }
        if (wanted.isEmpty() || names == null) return "";

        Map<String, Object> picked = new LinkedHashMap<>();
        Map<String, Object> flat = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            flat.put(names[i], args[i]);
            // @RequestBody Map 里的键也要能按名取到
            if (args[i] instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    flat.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        for (String key : wanted) {
            Object v = flat.get(key);
            if (v == null) continue;
            // BigDecimal 按数值输出，避免科学计数法让日志看不懂
            picked.put(key, v instanceof Number ? String.valueOf(v) : truncate(String.valueOf(v), 200));
        }
        return picked.isEmpty() ? "" : picked.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
