package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "course")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String courseName;
    @Column(length = 50)
    private String teacher;
    @Column(length = 100)
    private String classroom;
    @Column(length = 20)
    private String credits;
    @Column(length = 500)
    private String description;
    private Integer dayOfWeek;
    private Integer startSection;
    private Integer endSection;
    @Column(length = 20)
    private String weekRange;
    private Long studentId;
    @Column(length = 50)
    private String semester;
}
