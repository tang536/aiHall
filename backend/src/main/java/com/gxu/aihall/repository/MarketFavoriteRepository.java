package com.gxu.aihall.repository;

import com.gxu.aihall.entity.MarketFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketFavoriteRepository extends JpaRepository<MarketFavorite, Long> {

    List<MarketFavorite> findByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<MarketFavorite> findByUserIdAndItemId(Long userId, Long itemId);

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    void deleteByUserIdAndItemId(Long userId, Long itemId);
}
