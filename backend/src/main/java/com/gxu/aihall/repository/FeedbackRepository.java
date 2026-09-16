package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {
    List<Feedback> findAllByOrderByCreateTimeDesc();
    List<Feedback> findByUserIdOrderByCreateTimeDesc(Long userId);
    List<Feedback> findByStatusOrderByCreateTimeDesc(String status);
    List<Feedback> findByTypeOrderByCreateTimeDesc(String type);
    long countByStatus(String status);
}
