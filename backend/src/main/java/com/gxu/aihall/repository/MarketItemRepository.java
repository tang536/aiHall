package com.gxu.aihall.repository;

import com.gxu.aihall.entity.MarketItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketItemRepository extends JpaRepository<MarketItem, Long>, JpaSpecificationExecutor<MarketItem> {
    List<MarketItem> findAllByOrderByCreateTimeDesc();
    List<MarketItem> findByStatusOrderByCreateTimeDesc(String status);
    List<MarketItem> findBySellerIdOrderByCreateTimeDesc(Long sellerId);
    List<MarketItem> findByStatusAndCategoryOrderByCreateTimeDesc(String status, String category);
    long countByStatus(String status);

    /**
     * 悲观锁读取商品（SELECT ... FOR UPDATE）：购买流程的第一步，
     * 保证并发下单时只有一个请求能抢到该商品。必须在事务中调用。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MarketItem m where m.id = :id")
    Optional<MarketItem> findByIdForUpdate(@Param("id") Long id);

    /**
     * 关键词检索（走 ngram 全文索引）。
     * <p>用原生 SQL 的原因：JPQL / Criteria 表达不了 {@code MATCH ... AGAINST}。
     * 空串表示「该维度不过滤」；排序必须写在 SQL 里（Pageable 的 Sort 对原生查询不生效），
     * 分页则交给 Pageable 的 setFirstResult/setMaxResults。
     * <p>调用前必须确认 {@code KeywordSearchSupport#canUseFullText} 为真——
     * 索引不存在或检索词短于 ngram 粒度时这条 SQL 会白跑（返回 0 行）。
     */
    @Query(value = """
            SELECT * FROM market_item m
            WHERE (:status = '' OR m.status = :status)
              AND (:category = '' OR m.category = :category)
              AND MATCH(m.title, m.description) AGAINST (:kw IN NATURAL LANGUAGE MODE)
            ORDER BY m.create_time DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM market_item m
            WHERE (:status = '' OR m.status = :status)
              AND (:category = '' OR m.category = :category)
              AND MATCH(m.title, m.description) AGAINST (:kw IN NATURAL LANGUAGE MODE)
            """,
            nativeQuery = true)
    Page<MarketItem> searchByKeyword(@Param("status") String status,
                                     @Param("category") String category,
                                     @Param("kw") String kw,
                                     Pageable pageable);
}
