package com.gxu.aihall.repository;

import com.gxu.aihall.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findByUserIdOrderByCreateTimeDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);
}
