package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByCategoryOrderByIsTopDescPublishTimeDesc(String category);
    List<Notification> findByTitleContainingIgnoreCaseOrderByIsTopDescPublishTimeDesc(String keyword);
    List<Notification> findAllByOrderByIsTopDescPublishTimeDesc();
    List<Notification> findByIsEmergencyTrueAndStatusOrderByPublishTimeDesc(Integer status);
    boolean existsByTitle(String title);
}
