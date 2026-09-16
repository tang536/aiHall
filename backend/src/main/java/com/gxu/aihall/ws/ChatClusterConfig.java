package com.gxu.aihall.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 集群模式下的广播订阅容器。
 * <p>只在 {@code app.session.store=redis} 时创建 —— 单机模式没有 Redis，
 * 建这个容器只会不停报连接失败。
 * <p>这里<b>不注册任何固定订阅</b>：频道是按用户分的（{@code aihall:chat:user:{id}}），
 * 由 {@link ChatBroadcaster} 在用户连接/断开时动态增删。容器空订阅是安全的
 * （Spring Data Redis 的 SUBSCRIBE 会对空频道集合直接跳过）。
 * 这样做的意义是：空闲实例不为任何用户背订阅，消息也只落到真正持有该用户连接的实例上。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.session.store", havingValue = "redis")
public class ChatClusterConfig {

    @Bean
    public RedisMessageListenerContainer chatBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        log.info("私聊广播容器已就绪：订阅关系随用户连接动态增删");
        return container;
    }
}
