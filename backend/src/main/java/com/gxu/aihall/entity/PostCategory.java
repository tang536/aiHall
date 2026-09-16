package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 论坛帖子分类
 * code: 分类编码（唯一，如 CAMPUS / STUDY / LOST / HELP / OTHER）
 * name: 分类显示名称
 * sort: 排序值，越小越靠前
 * status: 1 启用 / 0 禁用
 */
@Data
@Entity
@Table(name = "post_category")
public class PostCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    private Integer sort = 0;

    private Integer status = 1;

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
