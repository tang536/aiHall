package com.gxu.aihall.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private String college;
    /** 账户余额（二手交易使用），登录时一并返回，前端可直接展示 */
    private BigDecimal balance;
}
