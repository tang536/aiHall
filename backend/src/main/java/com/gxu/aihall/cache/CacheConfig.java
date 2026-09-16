package com.gxu.aihall.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 轻量热点缓存：Spring Cache 抽象 + 自带的 TTL/容量双限实现。
 * <p><b>为什么不用 Caffeine</b>：本机构建环境的 Maven 仓库访问不到外网镜像（新依赖一律 404），
 * 装不了。这里用一个「LRU + 过期时间」的简单实现顶上 —— 校园规模下缓存条目最多几百个，
 * 用不着 Caffeine 的 W-TinyLFU 淘汰算法。
 * <p>三条设计纪律：
 * <ul>
 *   <li><b>TTL 要短</b>（列表 30 秒）：缓存是为了挡住「首页并发刷新」这类脉冲，不是为了长期存储。
 *       短 TTL 让「忘记失效」的代价上限只有 30 秒。</li>
 *   <li><b>写时全清</b>：帖子/商品一旦有写操作就清空对应命名空间。宁可多查一次库，
 *       也不要让用户看到自己刚发的帖子不在列表里。</li>
 *   <li><b>可开关</b>：{@code app.cache.enabled=false} 直接关掉（关掉时用空实现，业务代码无需分支）。</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String POST_LIST = "postList";
    public static final String MARKET_LIST = "marketList";
    public static final String STATS = "stats";
    /** 校园路网等几乎不变的参考数据 */
    public static final String REFERENCE = "reference";

    @Bean
    public CacheManager cacheManager(org.springframework.core.env.Environment env) {
        boolean enabled = env.getProperty("app.cache.enabled", Boolean.class, true);
        long listTtl = env.getProperty("app.cache.list-ttl-seconds", Long.class, 30L);
        long refTtl = env.getProperty("app.cache.reference-ttl-seconds", Long.class, 300L);
        int maxEntries = env.getProperty("app.cache.max-entries", Integer.class, 500);

        Map<String, Long> ttlByName = Map.of(
                POST_LIST, listTtl,
                MARKET_LIST, listTtl,
                STATS, listTtl,
                REFERENCE, refTtl);

        if (!enabled) {
            log.info("热点缓存：已关闭（app.cache.enabled=false）");
        } else {
            log.info("热点缓存：已启用（列表 {}s / 参考数据 {}s / 单命名空间上限 {} 条）",
                    listTtl, refTtl, maxEntries);
        }
        return new TtlCacheManager(enabled, ttlByName, maxEntries, refTtl);
    }

    /** 按命名空间给不同 TTL 的缓存管理器 */
    static class TtlCacheManager extends AbstractCacheManager {
        private final Map<String, Cache> caches = new ConcurrentHashMap<>();
        private final boolean enabled;
        private final Map<String, Long> ttlByName;
        private final int maxEntries;
        private final long defaultTtl;

        TtlCacheManager(boolean enabled, Map<String, Long> ttlByName, int maxEntries, long defaultTtl) {
            this.enabled = enabled;
            this.ttlByName = ttlByName;
            this.maxEntries = maxEntries;
            this.defaultTtl = defaultTtl;
        }

        @Override
        protected Collection<? extends Cache> loadCaches() {
            return java.util.List.of();
        }

        @Override
        public Cache getCache(String name) {
            return caches.computeIfAbsent(name, n ->
                    enabled ? new TtlCache(n, ttlByName.getOrDefault(n, defaultTtl), maxEntries)
                            : new NoOpCache(n));
        }
    }

    /** 关闭缓存时用的空实现：读永远 miss、写忽略，保证业务代码不需要判断开关 */
    static class NoOpCache implements Cache {
        private final String name;
        NoOpCache(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public Object getNativeCache() { return this; }
        @Override public ValueWrapper get(Object key) { return null; }
        @Override public <T> T get(Object key, Class<T> type) { return null; }
        @Override public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }
        @Override public void put(Object key, Object value) { }
        @Override public void evict(Object key) { }
        @Override public void clear() { }
    }

    /**
     * LRU + TTL 缓存。用 LinkedHashMap(accessOrder=true) 实现 LRU，整体加锁 ——
     * 命中路径本来就只有一次哈希查找的量级，锁竞争在这个 QPS 下不是瓶颈，
     * 换来的是实现简单、不会有并发下的容量失控。
     */
    static class TtlCache implements Cache {
        private final String name;
        private final long ttlMillis;
        private final int maxEntries;
        private final LinkedHashMap<Object, Slot> store;
        final AtomicLong hits = new AtomicLong();
        final AtomicLong misses = new AtomicLong();

        TtlCache(String name, long ttlSeconds, int maxEntries) {
            this.name = name;
            this.ttlMillis = Math.max(1, ttlSeconds) * 1000L;
            this.maxEntries = Math.max(1, maxEntries);
            this.store = new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Object, Slot> eldest) {
                    return size() > TtlCache.this.maxEntries;
                }
            };
        }

        record Slot(Object value, long expireAt) {
            boolean expired(long now) { return now >= expireAt; }
        }

        @Override public String getName() { return name; }
        @Override public Object getNativeCache() { return store; }

        @Override
        public ValueWrapper get(Object key) {
            Slot e = lookup(key);
            return e == null ? null : () -> e.value();
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            Slot e = lookup(key);
            if (e == null) return null;
            return type != null && !type.isInstance(e.value()) ? null : type.cast(e.value());
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            Slot e = lookup(key);
            if (e != null) return (T) e.value();
            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception ex) {
                throw new ValueRetrievalException(key, valueLoader, ex);
            }
        }

        @Override
        public void put(Object key, Object value) {
            synchronized (store) {
                store.put(key, new Slot(value, System.currentTimeMillis() + ttlMillis));
            }
        }

        @Override
        public void evict(Object key) {
            synchronized (store) {
                store.remove(key);
            }
        }

        @Override
        public void clear() {
            synchronized (store) {
                store.clear();
            }
        }

        private Slot lookup(Object key) {
            long now = System.currentTimeMillis();
            synchronized (store) {
                Slot e = store.get(key);
                if (e == null) {
                    misses.incrementAndGet();
                    return null;
                }
                if (e.expired(now)) {
                    store.remove(key);
                    misses.incrementAndGet();
                    return null;
                }
                hits.incrementAndGet();
                return e;
            }
        }

        /** 命中/未命中统计，供运维查看缓存是否真的在起作用 */
        public Map<String, Object> stats() {
            long h = hits.get();
            long m = misses.get();
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", name);
            s.put("size", store.size());
            s.put("maxEntries", maxEntries);
            s.put("hits", h);
            s.put("misses", m);
            s.put("hitRate", (h + m) == 0 ? 0d : Math.round(h * 1000d / (h + m)) / 1000d);
            return s;
        }
    }

    /** 便捷包装：拿一个值，没有就现算（本类内部用的查询入口，不依赖注解代理） */
    public static <T> T withCache(CacheManager manager, String name, Object key,
                                  Class<?> type, Supplier<T> loader, long ttlSeconds) {
        Cache cache = manager.getCache(name);
        if (cache == null) return loader.get();
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            Object v = wrapper.get();
            if (type == null || type.isInstance(v)) return (T) v;
        }
        T value = loader.get();
        if (value != null) cache.put(key, value);
        return value;
    }
}
