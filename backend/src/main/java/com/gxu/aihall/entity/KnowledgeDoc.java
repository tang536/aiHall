package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_doc")
public class KnowledgeDoc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(length = 100)
    private String category;
    @Column(length = 500)
    private String source;
    @Column(length = 500)
    private String keywords;
    private Integer priority = 0;
    private Long uploaderId;
    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
    private Integer status = 1;
}
