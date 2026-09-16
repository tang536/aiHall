package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam")
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String examName;
    private LocalDateTime examTime;
    @Column(length = 100)
    private String location;
    @Column(length = 20)
    private String seatNumber;
    @Column(length = 50)
    private String examType;
    @Column(length = 500)
    private String description;
    private Long studentId;
    @Column(length = 50)
    private String semester;
    private Integer status = 0;
}
