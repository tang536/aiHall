package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 二手商品收藏记录
 * 一个用户可以收藏多个商品，一个商品可以被多个用户收藏；(userId, itemId) 唯一
 */
@Data
@Entity
@Table(name = "market_favorite", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "item_id"})
})
public class MarketFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    private LocalDateTime createTime = LocalDateTime.now();
}
