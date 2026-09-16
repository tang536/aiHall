package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友关系（含好友申请）
 * status: PENDING 待接受 / ACCEPTED 已接受 / REJECTED 已拒绝
 */
@Data
@Entity
@Table(name = "friendship")
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发起方（申请人）用户 id */
    @Column(nullable = false)
    private Long requesterId;

    /** 接收方（被申请人）用户 id */
    @Column(nullable = false)
    private Long addresseeId;

    @Column(length = 20)
    private String status = "PENDING";

    /** 好友备注 */
    @Column(length = 50)
    private String remark;

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
