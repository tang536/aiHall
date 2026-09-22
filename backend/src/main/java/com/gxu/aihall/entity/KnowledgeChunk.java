package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文档分块（RAG Chunk）
 * 长文档按段落切分为固定大小的块，每块独立建立索引，
 * 检索时基于块级别的相似度计算，提升长文档的检索精度。
 */
@Data
@Entity
@Table(name = "knowledge_chunk", indexes = {
        @Index(name = "idx_chunk_doc_id", columnList = "docId"),
        @Index(name = "idx_chunk_category", columnList = "category")
})
public class KnowledgeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的知识库文档ID */
    @Column(nullable = false)
    private Long docId;

    /** 文档标题（冗余存储，便于检索结果展示） */
    @Column(length = 200)
    private String title;

    /** 块在文档中的索引（从0开始） */
    @Column(nullable = false)
    private Integer chunkIndex;

    /** 块内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 分类（冗余存储） */
    @Column(length = 100)
    private String category;

    /** 来源（冗余存储） */
    @Column(length = 500)
    private String source;

    /** 块的词元数量 */
    private Integer tokenCount = 0;

    /** 优先级（冗余文档的优先级） */
    private Integer priority = 0;

    private LocalDateTime createTime = LocalDateTime.now();
}
