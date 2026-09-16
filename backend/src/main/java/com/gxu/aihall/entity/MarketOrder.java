package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二手交易订单
 * status: PAID 已支付 / COMPLETED 已完成 / CANCELLED 已取消
 */
@Data
@Entity
@Table(name = "market_order", indexes = {
        @Index(name = "uk_market_order_request", columnList = "request_id", unique = true)
})
public class MarketOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 40)
    private String orderNo;

    /**
     * 幂等键：由客户端在 Idempotency-Key 头里传入（缺省时服务端按「买家+商品」兜底生成）。
     * 网络重试、用户连点都会命中同一条记录，保证不会重复扣款。
     */
    @Column(length = 64)
    private String requestId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long sellerId;

    /** 成交金额 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 商品标题快照（商品被删除后订单仍可读） */
    @Column(length = 100)
    private String itemTitle;

    @Column(length = 20)
    private String status = "PAID";

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
