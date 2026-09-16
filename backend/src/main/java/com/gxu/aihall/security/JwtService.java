package com.gxu.aihall.security;

import com.gxu.aihall.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256）。
 * <p>令牌自包含用户身份（sub / username / role）与唯一标识 jti、过期时间 exp，
 * 因此鉴权不再依赖服务端保存「token → userId」映射；服务端只在需要
 * 「单设备登录 / 主动登出」这类语义时才查询 {@link AuthStore}。
 */
@Slf4j
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration ttl;

    @Autowired
    public JwtService(@Value("${app.jwt.signing-key}") String signingKey,
                      @Value("${app.jwt.issuer:ai-student-hall}") String issuer,
                      @Value("${app.jwt.expire-hours:24}") long expireHours) {
        this(signingKey, issuer, Duration.ofHours(Math.max(1, expireHours)));
    }

    /** 允许直接指定有效期，便于单元测试覆盖过期场景 */
    public JwtService(String signingKey, String issuer, Duration ttl) {
        byte[] keyBytes = signingKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.signing-key 至少需要 32 字节（HS256 要求）");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
        this.ttl = ttl;
    }

    /** 令牌有效期，供会话存储设置相同的 TTL */
    public Duration getTtl() {
        return ttl;
    }
    // 注意：JWT 的 iat / exp 以「秒」为单位（RFC 7519 NumericDate），
    // 因此配置的有效期最小粒度为 1 秒；线上按小时配置（默认 24h）不受影响。

    /** 为指定用户签发令牌 */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(tokenId)
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, tokenId, ttl);
    }

    /**
     * 续签：用<b>同一个 jti</b> 重新签发一枚全新有效期的令牌（滑动续期）。
     * <p>为什么不换新 jti：单设备登录是靠「活跃会话 + jti」实现的，换 jti 意味着
     * 旧令牌<b>立刻</b>失效。用户手上还可能有并发请求（前端同时发多个接口）正在用旧令牌，
     * 它们会在服务端被判定为「已被顶下线」而 401 —— 表现为随机掉线。
     * 保持 jti 不变、只延长 exp，新旧两枚令牌在各自有效期内都可用，过渡是平滑的。
     * @param payload 已校验通过的旧令牌载荷
     */
    public IssuedToken renew(JwtPayload payload) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(payload.userId()))
                .id(payload.tokenId())
                .claim("username", payload.username())
                .claim("role", payload.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, payload.tokenId(), ttl);
    }

    /**
     * 解析并校验令牌（签名 + 签发方 + 有效期）。
     * 任何异常（签名被篡改、已过期、格式非法）统一返回 {@code null}，调用方无需关心细节。
     */
    public JwtPayload parse(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            return new JwtPayload(userId,
                    claims.get("username", String.class),
                    claims.get("role", String.class),
                    claims.getId(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return null;
        }
    }
}
