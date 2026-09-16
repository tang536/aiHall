package com.gxu.aihall.dto;

import lombok.Data;

/**
 * 对外公开的用户信息（统一脱敏）。
 * 帖子、二手商品、好友、私聊等任何「展示他人信息」的场景都必须使用本 VO，
 * 绝不包含 password / phone / email / 完整学号。
 */
@Data
public class PublicUserVO {
    private Long userId;

    /** 展示名：优先真实姓名，为空时回落到脱敏账号 */
    private String displayName;

    private String avatar;

    /** 学院 */
    private String college;

    /** 专业 */
    private String major;

    /** 年级 */
    private String grade;

    /** 脱敏后的平台账号（学号），如 2023****12 */
    private String maskedAccount;

    /** 当前登录用户与该用户是否已是好友 */
    private Boolean friend = false;
}
