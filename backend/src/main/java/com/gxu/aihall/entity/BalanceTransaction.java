package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额流水（审计用途，与余额变更在同一事务内写入）
 * type: RECHARGE 充值 / PAY 支出 / INCOME 收入 / ADMIN_ADJUST 管理员调整
 */
@Data
@Entity
@Table(name = "balance_transaction")
public class BalanceTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 变动金额，支出为负、收入为正 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 变动后余额（用于对账） */
    @Column(precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 20)
    private String type;

    @Column(length = 200)
    private String remark;

    /** 关联订单号（充值/管理员调整时为 null） */
    @Column(length = 40)
    private String relatedOrderNo;

    private LocalDateTime createTime = LocalDateTime.now();
}
