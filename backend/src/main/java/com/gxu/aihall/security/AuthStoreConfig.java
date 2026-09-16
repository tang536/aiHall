package com.gxu.aihall.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 认证状态存储的选择开关。
 * <p>{@code app.session.store=memory}（默认）：单进程内存，开箱即用；
 * {@code app.session.store=redis}：使用 Redis 共享存储，支持多实例部署与重启续期。
 * <p>切换只影响这一处，业务代码统一依赖 {@link AuthStore} 接口。
 */
@Slf4j
@Configuration
public class AuthStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "app.session.store", havingValue = "redis")
    public AuthStore redisAuthStore(StringRedisTemplate redis,
                                    @Value("${app.session.key-prefix:aihall:auth:}") String prefix) {
        AuthStore store = new RedisAuthStore(redis, prefix);
        try (RedisConnection conn = redis.getConnectionFactory().getConnection()) {
            conn.ping();
            log.info("认证状态存储：Redis 已连接（键前缀 {}）——单设备登录、登出黑名单与频控将跨实例共享", prefix);
        } catch (Exception e) {
            log.warn("认证状态存储：已配置为 Redis 但当前连接不可用（{}）。"
                    + "请检查 Redis 服务，或改回 app.session.store=memory", e.getMessage());
        }
        return store;
    }

    @Bean
    @ConditionalOnProperty(name = "app.session.store", havingValue = "memory", matchIfMissing = true)
    public AuthStore inMemoryAuthStore() {
        log.info("认证状态存储：进程内存（单机模式）——多实例部署请设置 app.session.store=redis");
        return new InMemoryAuthStore();
    }
}
