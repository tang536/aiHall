package com.gxu.aihall.repository;

import com.gxu.aihall.entity.BalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {
    List<BalanceTransaction> findByUserIdOrderByCreateTimeDesc(Long userId);
    long countByType(String type);
}
