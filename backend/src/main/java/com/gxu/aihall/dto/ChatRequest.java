package com.gxu.aihall.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String sessionId;
    private Long userId;
}
