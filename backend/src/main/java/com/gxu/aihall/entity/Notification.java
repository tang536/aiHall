package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(length = 50)
    private String category;
    @Column(length = 100)
    private String department;
    private Long publisherId;
    private Boolean isTop = false;
    private Boolean isEmergency = false;
    @Column(length = 500)
    private String summary;
    private Integer viewCount = 0;
    private LocalDateTime publishTime = LocalDateTime.now();
    private LocalDateTime createTime = LocalDateTime.now();
    private Integer status = 1;
}
