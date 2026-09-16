package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 私聊消息
 * msgType: TEXT 文本 / IMAGE 图片 / ITEM 分享的商品卡片 / POST 分享的帖子卡片
 * conversationId: 由双方用户 id 升序拼接（如 "12_35"），保证同一对用户只有一条会话
 */
@Data
@Entity
@Table(name = "private_message")
public class PrivateMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String conversationId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private Long receiverId;

    @Column(length = 2000)
    private String content;

    @Column(length = 20)
    private String msgType = "TEXT";

    /** 分享来源类型：MARKET / POST */
    @Column(length = 20)
    private String refType;

    /** 分享来源的 id（商品 id 或帖子 id） */
    private Long refId;

    /** 0 未读 / 1 已读 */
    private Integer isRead = 0;

    private LocalDateTime createTime = LocalDateTime.now();

    /** 生成会话 id：双方 id 升序，保证 a→b 与 b→a 落在同一会话 */
    public static String buildConversationId(Long a, Long b) {
        if (a == null || b == null) return null;
        return a <= b ? a + "_" + b : b + "_" + a;
    }
}
