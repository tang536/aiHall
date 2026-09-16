package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 个人通知（与系统公告 Notification 分离）
 * type: FRIEND_REQUEST 好友申请 / ORDER_PURCHASE 商品被购买 / SYSTEM 系统通知
 * relatedId: 关联的业务 id（好友申请 id / 订单 id 等），用于点击跳转
 */
@Data
@Entity
@Table(name = "user_notification", indexes = {
        @Index(name = "idx_user_notification_user", columnList = "user_id"),
        @Index(name = "idx_user_notification_read", columnList = "is_read")
})
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收者用户 id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String type = "SYSTEM";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 关联业务 id，便于点击后跳转 */
    private Long relatedId;

    @Column(name = "is_read")
    private Boolean isRead = false;

    private LocalDateTime createTime = LocalDateTime.now();
}
