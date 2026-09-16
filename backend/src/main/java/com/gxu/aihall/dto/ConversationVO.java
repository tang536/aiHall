package com.gxu.aihall.dto;

import com.gxu.aihall.entity.PrivateMessage;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私聊会话摘要（会话列表用）
 */
@Data
public class ConversationVO {
    /** 对话对方的公开信息（已脱敏） */
    private PublicUserVO peer;

    /** 最后一条消息 */
    private String lastMessage;

    private String lastMsgType;

    private LocalDateTime lastTime;

    /** 是否由对方发来（用于列表展示「我：」前缀） */
    private Boolean fromMe;

    /** 该会话中我未读的消息数 */
    private long unread;
}
