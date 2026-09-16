package com.gxu.aihall.repository;

import com.gxu.aihall.entity.KnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, Long> {
    List<KnowledgeDoc> findByCategoryOrderByPriorityDescCreateTimeDesc(String category);
    List<KnowledgeDoc> findByTitleContainingIgnoreCaseOrKeywordsContainingIgnoreCaseOrderByPriorityDesc(String title, String keywords);
    List<KnowledgeDoc> findByStatusOrderByPriorityDescCreateTimeDesc(Integer status);
    List<KnowledgeDoc> findAllByOrderByPriorityDescCreateTimeDesc();
    boolean existsByTitle(String title);
}
