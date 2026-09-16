package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByCategory(String category);
    List<Location> findByNameContainingIgnoreCase(String keyword);
    List<Location> findByCategoryOrderBySortOrderAsc(String category);
    List<Location> findAllByOrderBySortOrderAsc();
}
