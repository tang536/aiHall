package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 帖子回复（支持回复某条回复，仅两层：parentId 为空表示直接回复帖子）
 * status: PUBLISHED 正常 / DELETED 已删除（软删）
 */
@Data
@Entity
@Table(name = "post_reply")
public class PostReply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    /** 回复者用户 id */
    @Column(nullable = false)
    private Long userId;

    /** 被回复的那条回复的 id；为空表示直接回复帖子 */
    private Long parentId;

    /** 被回复者用户 id（用于展示「回复 @某某」） */
    private Long replyToUserId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(length = 20)
    private String status = "PUBLISHED";

    private LocalDateTime createTime = LocalDateTime.now();
}
