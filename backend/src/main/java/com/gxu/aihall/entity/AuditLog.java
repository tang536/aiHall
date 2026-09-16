package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 敏感操作审计日志。
 * <p>记「谁在什么时候对谁做了什么、成功还是失败」。用于事后追溯：
 * 余额调整、封禁解封、强制上下架、删除帖子/商品 这类管理员操作以前只改了业务数据，
 * 出了纠纷查不到责任人。
 * <p>写入由 {@code audit/AuditAspect} 切面统一完成，业务代码只需在被审计的方法上打
 * {@code @Audited}。采用 REQUIRES_NEW 独立事务 —— 业务方法回滚时操作记录仍然保留
 * （「尝试过了但失败」本身就是需要知道的信息）。
 */
@Data
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_create_time", columnList = "create_time"),
        @Index(name = "idx_audit_operator", columnList = "operator_id"),
        @Index(name = "idx_audit_target", columnList = "target_type, target_id")
})
public class AuditLog {

    public static final int MAX_DETAIL = 900;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作人 id（取不到时为 null —— 例如后台脚本触发） */
    private Long operatorId;

    /** 操作人账号快照：账号改名后仍能看出当时是谁 */
    @Column(length = 50)
    private String operatorName;

    /** 操作类型代码，如 ADJUST_BALANCE / BAN_USER */
    @Column(nullable = false, length = 40)
    private String action;

    /** 对象类型，如 USER / POST / MARKET_ITEM */
    @Column(length = 30)
    private String targetType;

    /** 对象 id */
    private Long targetId;

    /**
     * 关键参数摘要（<b>只放白名单字段</b>：路径变量 + 注解声明 {@code includeParams} 的 body 键）。
     * 绝不整份 dump 请求体 —— 里面可能有密码、手机、邮箱。
     */
    @Column(length = 1000)
    private String detail;

    /** SUCCESS / FAILURE */
    @Column(length = 10)
    private String result;

    /** 失败原因（result=FAILURE 时填，截断到 500 字避免异常信息过长撑爆列） */
    @Column(length = 500)
    private String errorMsg;

    /** 来源 IP */
    @Column(length = 64)
    private String ip;

    private LocalDateTime createTime;
}
