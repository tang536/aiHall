package com.gxu.aihall.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String realName;
    private String college;
    private String major;
    private String grade;
    private String phone;
    private String email;
    private String captchaId;
    private String captchaCode;
}
