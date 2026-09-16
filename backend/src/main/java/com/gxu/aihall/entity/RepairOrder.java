package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "repair_order")
public class RepairOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, length = 30)
    private String orderNo;
    @Column(nullable = false, length = 50)
    private String faultType;
    @Column(nullable = false, length = 200)
    private String location;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(length = 1000)
    private String imageUrls;
    private Long studentId;
    @Column(length = 50)
    private String contactName;
    @Column(length = 20)
    private String contactPhone;
    @Column(length = 20)
    private String status;
    @Column(length = 50)
    private String handler;
    private LocalDateTime expectCompleteTime;
    @Column(length = 500)
    private String handlerRemark;
    private Integer rating;
    @Column(length = 500)
    private String evaluation;
    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
    private LocalDateTime completeTime;
}
