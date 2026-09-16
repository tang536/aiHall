package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 校园平台绑定记录（教务系统）
 * 绑定成功后仅保存会话 Cookie 与绑定账号，不保存密码
 */
@Data
@Entity
@Table(name = "user_binding")
public class UserBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本系统用户 id */
    @Column(nullable = false)
    private Long userId;

    /** 绑定平台：JWXT 教务系统 */
    @Column(nullable = false, length = 20)
    private String platform;

    /** 在目标平台绑定成功的账号 */
    @Column(length = 50)
    private String bindAccount;

    /** 登录成功后保存的目标平台会话 Cookie */
    @Column(length = 4000)
    private String cookie;

    /** 加密存储的平台密码（AES 加密，用于 cookie 过期后自动重新登录） */
    @Column(length = 500)
    private String encryptedPassword;

    /** 1=已绑定 */
    private Integer status = 1;

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
