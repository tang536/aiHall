package com.gxu.aihall.security;

import java.time.Duration;

/**
 * 认证相关的服务端状态存储。
 * <p>JWT 本身是无状态的，但下面三类语义必须依赖服务端共享状态：
 * <ul>
 *   <li><b>单设备登录</b>：每个用户只保留一个活跃令牌，新登录把旧令牌顶下线；</li>
 *   <li><b>主动登出</b>：JWT 无法自行作废，登出时把 jti 放进黑名单直到自然过期；</li>
 *   <li><b>频控与验证码</b>：登录失败锁定、注册冷却、图形验证码等带 TTL 的短时状态。</li>
 * </ul>
 * <p>提供了内存实现（单机开箱即用）与 Redis 实现（多实例共享、重启不丢），
 * 由 {@code app.session.store} 切换，业务代码只依赖本接口。
 */
public interface AuthStore {

    /** 当前实现名称，用于启动日志与排查 */
    String name();

    // ==================== 会话（单设备登录） ====================

    /** 把 tokenId 登记为 userId 的唯一活跃会话 */
    void bindActiveSession(long userId, String tokenId, Duration ttl);

    /** 查询 userId 当前活跃会话的 tokenId，无则返回 null */
    String getActiveSession(long userId);

    /** 清除 userId 的活跃会话 */
    void clearActiveSession(long userId);

    /** 把 tokenId 加入黑名单（用于登出后立即失效），保留到令牌自然过期 */
    void blacklist(String tokenId, Duration ttl);

    /** tokenId 是否已被拉黑 */
    boolean isBlacklisted(String tokenId);

    // ==================== 通用短时状态 ====================

    /** 写入带过期时间的字符串值 */
    void put(String key, String value, Duration ttl);

    /** 读取并删除（原子），用于一次性验证码 */
    String take(String key);

    /** 读取计数值，不存在返回 0 */
    long count(String key);

    /** 计数 +1；key 首次写入时设置过期时间 */
    long increment(String key, Duration ttl);

    /** 删除键（计数清零） */
    void reset(String key);
}
