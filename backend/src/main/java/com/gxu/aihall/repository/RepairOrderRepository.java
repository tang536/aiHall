package com.gxu.aihall.repository;

import com.gxu.aihall.entity.RepairOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {
    Optional<RepairOrder> findByOrderNo(String orderNo);
    List<RepairOrder> findByStudentIdOrderByCreateTimeDesc(Long studentId);
    List<RepairOrder> findByStatusOrderByCreateTimeDesc(String status);
    List<RepairOrder> findAllByOrderByCreateTimeDesc();
    long countByStatus(String status);
    List<RepairOrder> findByCreateTimeAfter(LocalDateTime time);
}
