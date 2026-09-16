package com.gxu.aihall.security;

import java.time.Instant;

/**
 * 已校验通过的 JWT 载荷。
 * @param userId    用户 ID（对应 sub 声明）
 * @param username  平台账号
 * @param role      角色（STUDENT / ADMIN）
 * @param tokenId   令牌唯一标识（jti），用于单设备登录与登出黑名单
 * @param expiresAt 过期时间
 */
public record JwtPayload(Long userId, String username, String role, String tokenId, Instant expiresAt) {
}
