package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    List<Post> findAllByOrderByCreateTimeDesc();
    List<Post> findAllByStatusOrderByCreateTimeDesc(String status);
    List<Post> findByStatusAndCategoryOrderByCreateTimeDesc(String status, String category);
    List<Post> findByUserIdAndStatusOrderByCreateTimeDesc(Long userId, String status);
    long countByStatus(String status);

    /**
     * 关键词检索（走 ngram 全文索引）。
     * <p>空串表示「该维度不过滤」；排序写在 SQL 里（Pageable 的 Sort 对原生查询不生效），
     * 分页由 Pageable 的 setFirstResult/setMaxResults 完成。
     * 调用前须确认 {@code KeywordSearchSupport#canUseFullText} 为真。
     */
    @Query(value = """
            SELECT * FROM post p
            WHERE (:status = '' OR p.status = :status)
              AND (:category = '' OR p.category = :category)
              AND MATCH(p.title, p.content) AGAINST (:kw IN NATURAL LANGUAGE MODE)
            ORDER BY p.create_time DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM post p
            WHERE (:status = '' OR p.status = :status)
              AND (:category = '' OR p.category = :category)
              AND MATCH(p.title, p.content) AGAINST (:kw IN NATURAL LANGUAGE MODE)
            """,
            nativeQuery = true)
    Page<Post> searchByKeyword(@Param("status") String status,
                               @Param("category") String category,
                               @Param("kw") String kw,
                               Pageable pageable);
}
