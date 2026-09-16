package com.gxu.aihall.repository;

import com.gxu.aihall.entity.MarketOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, Long> {
    List<MarketOrder> findByBuyerIdOrderByCreateTimeDesc(Long buyerId);
    List<MarketOrder> findBySellerIdOrderByCreateTimeDesc(Long sellerId);
    Optional<MarketOrder> findByOrderNo(String orderNo);
    List<MarketOrder> findAllByOrderByCreateTimeDesc();
    long countByStatus(String status);
    boolean existsByItemIdAndStatus(Long itemId, String status);

    /** 幂等键命中时回查原订单，保证重试返回同一笔 */
    Optional<MarketOrder> findFirstByRequestId(String requestId);
}
