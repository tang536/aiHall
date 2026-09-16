package com.gxu.aihall.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学生对平台的反馈（管理员侧可查看并回复）
 * type: SUGGESTION 建议 / COMPLAINT 投诉 / BUG 故障 / OTHER 其他
 * status: PENDING 待处理 / PROCESSING 处理中 / RESOLVED 已解决
 */
@Data
@Entity
@Table(name = "feedback")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提交反馈的学生用户 id */
    @Column(nullable = false)
    private Long userId;

    @Column(length = 20)
    private String type = "SUGGESTION";

    @Column(length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    /** 联系方式（学生自愿填写，仅管理员可见） */
    @Column(length = 100)
    private String contact;

    /** 截图，多张以英文逗号分隔 */
    @Column(length = 1000)
    private String images;

    @Column(length = 20)
    private String status = "PENDING";

    @Column(length = 2000)
    private String adminReply;

    /** 回复的管理员 id */
    private Long adminId;

    private LocalDateTime replyTime;

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
