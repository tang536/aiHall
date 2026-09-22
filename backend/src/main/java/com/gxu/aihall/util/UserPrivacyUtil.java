package com.gxu.aihall.util;

import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.User;

import java.math.BigDecimal;

/**
 * 用户隐私脱敏工具。
 * 平台对外展示他人信息时，一律通过 {@link #toPublicVO(User, boolean)} 转换：
 * 只暴露展示名、头像、学院/专业/年级与完整学号，
 * 手机号、邮箱、密码绝不外泄。
 * 注：学号（username）按业务要求作为公开信息展示，不再脱敏。
 */
public final class UserPrivacyUtil {

    private UserPrivacyUtil() {
    }

    /** 账号脱敏：保留前 4 位与后 2 位，中间以 4 个星号代替（保留方法供其他场景使用） */
    public static String maskAccount(String username) {
        if (username == null || username.isEmpty()) return "";
        int len = username.length();
        if (len <= 2) return "*".repeat(len);
        if (len <= 6) return username.charAt(0) + "****";
        return username.substring(0, 4) + "****" + username.substring(len - 2);
    }

    /** 手机号脱敏：138****8888 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        if (phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 邮箱脱敏：abc***@qq.com */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 3) return name.charAt(0) + "***" + domain;
        return name.substring(0, 3) + "***" + domain;
    }

    /** 余额读取兜底：旧记录可能为 NULL */
    public static BigDecimal balanceOf(User user) {
        if (user == null || user.getBalance() == null) return BigDecimal.ZERO;
        return user.getBalance();
    }

    /** 转换为对外公开信息（不含任何隐私字段） */
    public static PublicUserVO toPublicVO(User user) {
        return toPublicVO(user, false);
    }

    public static PublicUserVO toPublicVO(User user, boolean isFriend) {
        PublicUserVO vo = new PublicUserVO();
        if (user == null) {
            vo.setUserId(null);
            vo.setDisplayName("已注销用户");
            vo.setMaskedAccount("");
            vo.setFriend(isFriend);
            return vo;
        }
        vo.setUserId(user.getId());
        String realName = user.getRealName();
        vo.setDisplayName(realName == null || realName.trim().isEmpty()
                ? user.getUsername() : realName.trim());
        vo.setAvatar(user.getAvatar());
        vo.setCollege(user.getCollege());
        vo.setMajor(user.getMajor());
        vo.setGrade(user.getGrade());
        // 学号按业务要求公开展示，不再脱敏
        vo.setMaskedAccount(user.getUsername());
        vo.setFriend(isFriend);
        return vo;
    }
}
