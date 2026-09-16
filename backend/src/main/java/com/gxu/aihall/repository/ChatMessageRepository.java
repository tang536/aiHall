package com.gxu.aihall.repository;

import com.gxu.aihall.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreateTimeAsc(String sessionId);
    List<ChatMessage> findByUserIdOrderByCreateTimeDesc(Long userId);
    long countByRole(String role);
    long countByFeedback(Integer feedback);
    List<ChatMessage> findByCreateTimeAfter(LocalDateTime time);
    List<ChatMessage> findByRoleAndCreateTimeAfter(String role, LocalDateTime time);
}
