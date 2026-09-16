package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二手交易商品
 * category: TEXTBOOK 教材书籍 / ELECTRONIC 电子数码 / DAILY 生活用品 / SPORTS 运动户外 / OTHER 其他
 * itemCondition: NEW 全新 / LIKE_NEW 几乎全新 / GOOD 轻微使用痕迹 / FAIR 明显使用痕迹
 * status: ON_SALE 在售 / OFF_SHELF 已下架 / SOLD 已售出
 */
@Data
@Entity
@Table(name = "market_item")
public class MarketItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 卖家（发布者）用户 id */
    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    /** 出售价格，金额一律使用 BigDecimal */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** 原价（可选，用于展示折扣） */
    @Column(precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(length = 30)
    private String category = "OTHER";

    @Column(length = 20)
    private String itemCondition = "GOOD";

    /** 商品图片，多张以英文逗号分隔（/uploads/xxx.png） */
    @Column(length = 1500)
    private String images;

    /** 交易地点（校园内面交地点） */
    @Column(length = 200)
    private String tradeLocation;

    @Column(length = 20)
    private String status = "ON_SALE";

    private Integer viewCount = 0;

    /** 分享次数 */
    private Integer shareCount = 0;

    /** 上线日期 */
    private LocalDateTime onlineTime = LocalDateTime.now();

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
