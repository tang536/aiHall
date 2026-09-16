package com.gxu.aihall.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内存实现：零外部依赖，适合单机开发与演示。
 * <p>注意：进程重启后状态清空。由于令牌本身是 JWT，重启后旧令牌仍能通过签名校验，
 * 此时「活跃会话」记录缺失会被视为未登记（放行），因此用户不会被动掉线。
 */
public class InMemoryAuthStore implements AuthStore {

    private static final String ACTIVE_PREFIX = "active:";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private record Entry(String value, long expireAt) {
        boolean expired(long now) {
            return expireAt > 0 && expireAt <= now;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong ops = new AtomicLong();

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public void bindActiveSession(long userId, String tokenId, Duration ttl) {
        put(ACTIVE_PREFIX + userId, tokenId, ttl);
    }

    @Override
    public String getActiveSession(long userId) {
        Entry e = read(ACTIVE_PREFIX + userId);
        return e == null ? null : e.value();
    }

    @Override
    public void clearActiveSession(long userId) {
        reset(ACTIVE_PREFIX + userId);
    }

    @Override
    public void blacklist(String tokenId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) return;
        put(BLACKLIST_PREFIX + tokenId, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        return read(BLACKLIST_PREFIX + tokenId) != null;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        long expireAt = ttl == null || ttl.isZero() || ttl.isNegative()
                ? 0 : System.currentTimeMillis() + ttl.toMillis();
        entries.put(key, new Entry(value, expireAt));
        sweepIfNeeded();
    }

    @Override
    public String take(String key) {
        Entry e = entries.remove(key);
        return e == null || e.expired(System.currentTimeMillis()) ? null : e.value();
    }

    @Override
    public long count(String key) {
        Entry e = read(key);
        if (e == null) return 0;
        try {
            return Long.parseLong(e.value());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public long increment(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        Entry updated = entries.compute(key, (k, current) -> {
            long value = 1;
            long expireAt = now + (ttl == null ? 0 : ttl.toMillis());
            if (current != null && !current.expired(now)) {
                expireAt = current.expireAt();
                try {
                    value = Long.parseLong(current.value()) + 1;
                } catch (NumberFormatException ignored) {
                    value = 1;
                }
            }
            return new Entry(String.valueOf(value), expireAt);
        });
        sweepIfNeeded();
        try {
            return Long.parseLong(updated.value());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public void reset(String key) {
        entries.remove(key);
    }

    private Entry read(String key) {
        Entry e = entries.get(key);
        if (e == null) return null;
        if (e.expired(System.currentTimeMillis())) {
            entries.remove(key, e);
            return null;
        }
        return e;
    }

    /** 懒清理：每 256 次写入顺带清理一次过期项，避免无界增长 */
    private void sweepIfNeeded() {
        if ((ops.incrementAndGet() & 0xFF) != 0) return;
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> e.getValue().expired(now));
    }
}
