package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    @Column(nullable = false, length = 100)
    private String password;
    @Column(nullable = false, length = 50)
    private String realName;
    @Column(length = 20)
    private String role;
    @Column(length = 100)
    private String college;
    @Column(length = 50)
    private String major;
    @Column(length = 20)
    private String grade;
    @Column(length = 20)
    private String phone;
    @Column(length = 100)
    private String email;
    @Column(length = 200)
    private String avatar;
    /**
     * 账户余额（二手交易使用）。
     * 注意：历史上线的库通过 ddl-auto=update 新增该列时旧记录会是 NULL，
     * 因此业务代码读取时统一使用 {@code getBalance() == null ? BigDecimal.ZERO : getBalance()}。
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    private Integer status = 1;
    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();

    /** 余额读取兜底：旧记录该列为 NULL 时按 0 处理（JPA 按字段访问，此方法不建列） */
    public BigDecimal balanceOrZero() {
        return balance == null ? BigDecimal.ZERO : balance;
    }
}
