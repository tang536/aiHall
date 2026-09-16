package com.gxu.aihall.audit;

import java.lang.annotation.*;

/**
 * 标记需要留痕的管理员敏感操作。
 * 由 {@code audit/AuditAspect} 在方法返回/抛异常时写 {@code audit_log}。
 * 用法：
 * {@code
 * @Audited(action = "ADJUST_BALANCE", targetType = "USER",
 *          targetIdParam = "id", includeParams = {"amount", "remark"})
 * @PostMapping("/users/{id}/balance")
 * public Result<Map<String, Object>> adjustBalance(@PathVariable Long id, @RequestBody Map<String,Object> body) {...}
 * }
 * 为什么用注解而不是在 Service 里手写 auditService.record(...)：
 * 手写会漏。新增一个敏感操作时，忘记补一行记录代码是很容易的，而且这种遗漏没有任何提示。
 * 注解把「哪些操作算敏感」集中到方法签名上，一眼可数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** 操作类型代码 */
    String action();

    /** 对象类型（表名语义，如 USER / POST / MARKET_ITEM） */
    String targetType() default "";

    /** 从哪个方法参数取对象 id（一般是 @PathVariable 的 id） */
    String targetIdParam() default "";

    /**
     * 额外记录哪些 body / 参数的键。
     * 白名单式—— 不列就不记，避免把密码、手机号、邮箱写进日志。
     */
    String[] includeParams() default {};
}
