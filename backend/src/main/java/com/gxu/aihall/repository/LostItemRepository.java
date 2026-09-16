package com.gxu.aihall.repository;

import com.gxu.aihall.entity.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {
    List<LostItem> findByTypeOrderByCreateTimeDesc(String type);
    List<LostItem> findByItemCategoryOrderByCreateTimeDesc(String category);
    List<LostItem> findAllByOrderByCreateTimeDesc();
    List<LostItem> findByTypeAndStatusOrderByCreateTimeDesc(String type, String status);
}
