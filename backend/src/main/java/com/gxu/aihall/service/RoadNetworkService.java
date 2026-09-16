package com.gxu.aihall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

/**
 * 校园路网数据服务：把原本随前端打包的 {@code road-network.json}（235KB）移到后端。
 * <p><b>为什么服务化</b>：这份数据随前端打包，每次改动都要重新构建两端；
 * 且 2135 条边 + 1835 个节点对纯静态页面是「首屏就全量下发」。
 * 移到后端后：前端按需拉取、后端内存缓存一次（数据几乎不变），
 * 将来要换成真实路网服务 / 增量更新时，前端一行都不用改。
 * <p>加载失败时不抛：导航本就有「降级为直线距离」的兜底路径，路网拿不到
 * 只是少了一个优化，不该把整个服务启动拖崩。
 */
@Slf4j
@Service
public class RoadNetworkService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 内存缓存：数据几乎不变，首次请求后常驻 */
    private volatile Map<String, Object> cached;

    /**
     * 返回路网数据（首次加载后缓存）。
     * @return 路网 JSON 对应的 Map，加载失败返回 null（由调用方决定是否降级）
     */
    public Map<String, Object> roadNetwork() {
        Map<String, Object> local = cached;
        if (local != null) return local;
        synchronized (this) {
            if (cached != null) return cached;
            try (InputStream in = new ClassPathResource("road-network.json").getInputStream()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(in, Map.class);
                cached = parsed;
                log.info("校园路网已加载：{} 节点 / {} 边",
                        ((java.util.List<?>) parsed.get("nodes")).size(),
                        ((java.util.List<?>) parsed.get("edges")).size());
                return parsed;
            } catch (Exception e) {
                log.error("校园路网数据加载失败（导航将降级为直线距离）: {}", e.getMessage());
                return null;
            }
        }
    }
}
