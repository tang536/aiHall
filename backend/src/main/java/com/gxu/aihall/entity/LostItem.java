package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "lost_item")
public class LostItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String type;
    @Column(nullable = false, length = 100)
    private String itemName;
    @Column(length = 50)
    private String itemCategory;
    @Column(length = 500)
    private String description;
    @Column(length = 200)
    private String lostLocation;
    private LocalDateTime lostTime;
    @Column(length = 50)
    private String contactName;
    @Column(length = 20)
    private String contactPhone;
    @Column(length = 500)
    private String imageUrl;
    private Long publisherId;
    @Column(length = 20)
    private String status;
    private LocalDateTime createTime = LocalDateTime.now();
    @Column(length = 500)
    private String keywords;
}
