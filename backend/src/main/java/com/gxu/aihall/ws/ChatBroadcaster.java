package com.gxu.aihall.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gxu.aihall.service.ChatSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 私聊消息投递入口：本机直投 + 跨实例广播（按用户分频道）。
 * <p><b>为什么需要它</b>：{@link ChatSessionRegistry} 是进程内的 Map，只记录连到本实例的连接。
 * 多实例部署时 A 连在实例 1、B 连在实例 2，实例 1 的内存里根本没有 B 的连接，
 * 消息推不过去 —— 表现为「消息落库了但对方看不到，刷新才出来」。
 * <p><b>做法</b>：投递时先在本机推一次（快、不依赖 Redis），再把消息发到
 * {@code aihall:chat:user:{userId}} 频道；其他实例订阅到之后，再推给自己持有的连接。
 * <p><b>为什么按用户分频道</b>：早期版本用一个公共频道，每条消息都会分发给所有实例，
 * 实例再多也只有 1 个实例真正持有目标连接 —— 其余全是白跑。改成按用户分频道后，
 * <b>只有持有该用户连接的实例会收到</b>（通常恰好 1 个）。订阅是随连接动态增删的：
 * 用户上线才订阅、最后一个连接断开才退订，空闲实例不背订阅。
 * <p><b>去重</b>：广播信封带 {@code origin}（实例 id）。发布者本身若也订阅了该用户频道
 * （即目标用户就挂在它身上），会收到自己发出的报文，靠 origin 跳过，避免推两遍。
 * <p><b>降级</b>：只有 {@code app.session.store=redis}（集群模式）才启用广播。
 * 单机模式（memory）下退化为「只本机直投」，行为与改造前完全一致，且不依赖 Redis 可用性。
 */
@Slf4j
@Component
public class ChatBroadcaster {

    /** 广播信封：origin 用于本机去重，payload 原样透传给接收方的 WebSocket */
    record Envelope(String origin, Long userId, Map<String, Object> payload) {
    }

    public static final String DEFAULT_CHANNEL_PREFIX = "aihall:chat:user:";

    private final ChatSessionRegistry sessionRegistry;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 用 ObjectProvider 而不是直接注入容器：容器 bean 只在 redis 模式存在，
     * 而反向依赖（容器需要回调本类）会形成循环注入。
     */
    private final ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider;

    /** 本实例标识：用于跳过自己发出的广播 */
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private final boolean clusterEnabled;
    private final String channelPrefix;

    /** 本实例已订阅的用户频道（避免重复订阅 / 重复退订） */
    private final Set<Long> subscribedUsers = ConcurrentHashMap.newKeySet();

    /**
     * 必须是<b>同一个实例</b>：容器的 removeMessageListener 按对象身份匹配，
     * 每次传新的 lambda/方法引用会导致退订不掉、订阅无限堆积。
     */
    private final org.springframework.data.redis.connection.MessageListener envelopeListener = this::handleEnvelope;

    private final AtomicLong localDelivered = new AtomicLong();
    private final AtomicLong broadcastSent = new AtomicLong();
    private final AtomicLong broadcastReceived = new AtomicLong();
    private final AtomicLong broadcastFailed = new AtomicLong();

    public ChatBroadcaster(ChatSessionRegistry sessionRegistry,
                          StringRedisTemplate redis,
                          ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider,
                          @Value("${app.session.store:memory}") String sessionStore,
                          @Value("${app.chat.channel-prefix:" + DEFAULT_CHANNEL_PREFIX + "}") String channelPrefix) {
        this.sessionRegistry = sessionRegistry;
        this.redis = redis;
        this.listenerContainerProvider = listenerContainerProvider;
        this.channelPrefix = channelPrefix;
        this.clusterEnabled = "redis".equalsIgnoreCase(sessionStore);
        if (clusterEnabled) {
            log.info("私聊投递：集群模式已启用（实例 {}），按用户分频道 {}", instanceId, channelPrefix + "{userId}");
        } else {
            log.info("私聊投递：单机模式，仅本机直投（多实例部署请设置 app.session.store=redis）");
        }
    }

    /** 用户频道名 */
    public String channelOf(Long userId) {
        return channelPrefix + userId;
    }

    /**
     * 本实例上出现该用户的连接 → 订阅它的频道。
     * 由 {@code ChatWebSocketHandler} 在连接建立后调用。
     */
    public void onUserConnected(Long userId) {
        if (!clusterEnabled || userId == null) return;
        if (!subscribedUsers.add(userId)) return;   // 已经订阅过了
        RedisMessageListenerContainer container = listenerContainerProvider.getIfAvailable();
        if (container == null) {
            subscribedUsers.remove(userId);
            log.warn("私聊广播订阅容器不可用，用户 {} 只能本机投递", userId);
            return;
        }
        try {
            container.addMessageListener(envelopeListener, new ChannelTopic(channelOf(userId)));
        } catch (Exception e) {
            subscribedUsers.remove(userId);
            broadcastFailed.incrementAndGet();
            log.warn("订阅用户 {} 的广播频道失败（不影响本机投递）: {}", userId, e.getMessage());
        }
    }

    /**
     * 本实例上该用户的连接断开 → 退订。
     * 注意调用方要先确认用户确实已不在线：连接被新连接顶替时，旧连接的关闭回调
     * 也会走到这里，此时用户其实还在线，退订会让消息推不过来。
     */
    public void onUserDisconnected(Long userId) {
        if (!clusterEnabled || userId == null) return;
        if (sessionRegistry.isOnline(userId)) return;   // 还有连接挂着，别退订
        if (!subscribedUsers.remove(userId)) return;
        RedisMessageListenerContainer container = listenerContainerProvider.getIfAvailable();
        if (container == null) return;
        try {
            container.removeMessageListener(envelopeListener, new ChannelTopic(channelOf(userId)));
        } catch (Exception e) {
            // 退订失败不致命：只是白收几条与己无关的消息
            log.debug("退订用户 {} 的广播频道失败: {}", userId, e.getMessage());
        }
    }

    /**
     * 把消息推给指定用户：先本机直投，再广播给持有该用户连接的其他实例。
     * @param payload 完整帧（如 {"type":"MESSAGE", ...}），原样透传
     * @return 本机是否成功推送到连接；false 不代表失败 —— 对方可能连在别的实例上（已广播过去）、
     *         或者确实离线（消息已落库，上线后拉历史即可）
     */
    public boolean pushToUser(Long userId, Map<String, Object> payload) {
        if (userId == null || payload == null) return false;

        boolean deliveredLocally = sessionRegistry.sendTo(userId, payload);
        if (deliveredLocally) {
            localDelivered.incrementAndGet();
        }

        if (clusterEnabled) {
            publish(new Envelope(instanceId, userId, payload));
        }
        return deliveredLocally;
    }

    /** 收到本实例订阅频道的报文（可能来自别的实例，也可能是自己发出的回声） */
    private void handleEnvelope(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        onClusterMessage(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
    }

    void onClusterMessage(String json) {
        Envelope envelope;
        try {
            envelope = objectMapper.readValue(json, Envelope.class);
        } catch (Exception e) {
            log.warn("私聊广播报文解析失败，已忽略: {}", e.getMessage());
            return;
        }
        if (envelope.userId() == null || envelope.payload() == null) return;
        if (instanceId.equals(envelope.origin())) {
            return; // 自己发的，本机已经直投过了，跳过避免重复
        }
        broadcastReceived.incrementAndGet();
        if (sessionRegistry.sendTo(envelope.userId(), envelope.payload())) {
            localDelivered.incrementAndGet();
        }
    }

    private void publish(Envelope envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redis.convertAndSend(channelOf(envelope.userId()), json);
            broadcastSent.incrementAndGet();
        } catch (Exception e) {
            // 广播失败不影响本机投递，也不影响消息可靠性（消息已落库）
            broadcastFailed.incrementAndGet();
            log.warn("私聊广播失败（本机投递不受影响）: {}", e.getMessage());
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    public boolean isClusterEnabled() {
        return clusterEnabled;
    }

    public String getChannelPrefix() {
        return channelPrefix;
    }

    /** 运行计数：排查「消息为什么没到」时，先看本实例有没有发出去 / 收到过广播 */
    public Map<String, Object> stats() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("instanceId", instanceId);
        m.put("clusterEnabled", clusterEnabled);
        m.put("channelPrefix", channelPrefix);
        m.put("subscribedUsers", subscribedUsers.size());
        m.put("localDelivered", localDelivered.get());
        m.put("broadcastSent", broadcastSent.get());
        m.put("broadcastReceived", broadcastReceived.get());
        m.put("broadcastFailed", broadcastFailed.get());
        return m;
    }
}
