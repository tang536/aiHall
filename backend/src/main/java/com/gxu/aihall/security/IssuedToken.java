package com.gxu.aihall.security;

import java.time.Duration;

/**
 * 新签发的令牌及其元信息。
 * @param token   完整 JWT 字符串
 * @param tokenId 令牌唯一标识（jti）
 * @param ttl     有效期
 */
public record IssuedToken(String token, String tokenId, Duration ttl) {
}
