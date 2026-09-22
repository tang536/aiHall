package com.gxu.aihall.repository;

import com.gxu.aihall.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    /** 根据文档ID查找所有分块，按块索引排序 */
    List<KnowledgeChunk> findByDocIdOrderByChunkIndexAsc(Long docId);

    /** 根据文档ID删除所有分块 */
    void deleteByDocId(Long docId);

    /** 查找所有分块 */
    List<KnowledgeChunk> findAll();

    /** 统计分块数量 */
    long count();
}
