package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Column(length = 50)
    private String sessionId;
    @Column(length = 20)
    private String role;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(length = 500)
    private String sources;
    private Integer feedback;
    private LocalDateTime createTime = LocalDateTime.now();
    private Long responseTime;
}
