package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "application")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, length = 30)
    private String applicationNo;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String formData;
    @Column(length = 1000)
    private String materialUrls;
    private Long studentId;
    @Column(length = 50)
    private String studentName;
    @Column(length = 20)
    private String status;
    @Column(length = 500)
    private String currentNode;
    @Column(length = 500)
    private String rejectReason;
    @Column(columnDefinition = "TEXT")
    private String approvalHistory;
    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
    private LocalDateTime completeTime;
}
