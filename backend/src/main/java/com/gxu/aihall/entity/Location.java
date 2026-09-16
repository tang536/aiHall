package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "location")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 50)
    private String category;
    @Column(length = 200)
    private String address;
    private Double longitude;
    private Double latitude;
    @Column(length = 500)
    private String description;
    @Column(length = 200)
    private String openHours;
    @Column(length = 20)
    private String phone;
    @Column(length = 500)
    private String imageUrl;
    private Integer floor;
    @Column(length = 100)
    private String building;
    private Integer sortOrder = 0;
}
