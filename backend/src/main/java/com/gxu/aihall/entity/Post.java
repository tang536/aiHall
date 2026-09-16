package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 校园论坛帖子
 * category: CAMPUS 校园动态 / STUDY 学习交流 / LOST 失物互助 / HELP 求助问答 / OTHER 其他
 * status: PUBLISHED 已发布 / DELETED 已删除（软删，作者或管理员操作）
 */
@Data
@Entity
@Table(name = "post")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作者用户 id */
    @Column(nullable = false)
    private Long userId;

    @Column(length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 30)
    private String category = "CAMPUS";

    /** 配图，多张以英文逗号分隔 */
    @Column(length = 1500)
    private String images;

    private Integer replyCount = 0;

    private Integer likeCount = 0;

    @Column(length = 20)
    private String status = "PUBLISHED";

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
