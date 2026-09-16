package com.gxu.aihall.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请对外视图（对方信息已脱敏）
 */
@Data
public class FriendRequestVO {
    private Long id;

    /** 申请涉及的另一方（收到申请时为申请人，发出申请时为接收人） */
    private PublicUserVO user;

    private String status;

    private LocalDateTime createTime;

    /** 是否为收到的申请（true=待我处理） */
    private Boolean incoming;
}
