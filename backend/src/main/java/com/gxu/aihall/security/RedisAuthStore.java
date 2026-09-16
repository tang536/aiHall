package com.gxu.aihall.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现：把登录态、黑名单、频控与验证码放到共享存储。
 * <p>相比内存实现带来的三点实际收益：
 * <ol>
 *   <li><b>多实例横向扩展</b>：多台后端共享同一份登录态，任意实例都能识别「被顶下线」；</li>
 *   <li><b>重启不丢会话</b>：服务重启后单设备登录关系与黑名单仍在，用户无需重新登录；</li>
 *   <li><b>统一 TTL</b>：过期清理交给 Redis，无需自行做懒清理。</li>
 * </ol>
 * <p>所有键统一加前缀（默认 {@code aihall:auth:}），便于与业务键隔离和整批清理。
 */
public class RedisAuthStore implements AuthStore {

    private static final String ACTIVE_PREFIX = "active:";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisAuthStore(StringRedisTemplate redis, String prefix) {
        this.redis = redis;
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public String name() {
        return "redis(" + prefix + ")";
    }

    @Override
    public void bindActiveSession(long userId, String tokenId, Duration ttl) {
        setRaw(ACTIVE_PREFIX + userId, tokenId, ttl);
    }

    @Override
    public String getActiveSession(long userId) {
        return redis.opsForValue().get(key(ACTIVE_PREFIX + userId));
    }

    @Override
    public void clearActiveSession(long userId) {
        redis.delete(key(ACTIVE_PREFIX + userId));
    }

    @Override
    public void blacklist(String tokenId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) return;
        setRaw(BLACKLIST_PREFIX + tokenId, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redis.hasKey(key(BLACKLIST_PREFIX + tokenId)));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        setRaw(key, value, ttl);
    }

    @Override
    public String take(String key) {
        return redis.opsForValue().getAndDelete(key(key));
    }

    @Override
    public long count(String key) {
        String raw = redis.opsForValue().get(key(key));
        if (raw == null) return 0;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key(key));
        long result = value == null ? 0 : value;
        // 首次计数时挂上过期时间，实现滑动窗口式频控
        if (result == 1 && ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            redis.expire(key(key), ttl.toSeconds() > 0 ? ttl.toSeconds() : 1, TimeUnit.SECONDS);
        }
        return result;
    }

    @Override
    public void reset(String key) {
        redis.delete(key(key));
    }

    private void setRaw(String logicalKey, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            redis.opsForValue().set(key(logicalKey), value);
        } else {
            redis.opsForValue().set(key(logicalKey), value, ttl);
        }
    }

    private String key(String logicalKey) {
        return prefix + logicalKey;
    }
}
