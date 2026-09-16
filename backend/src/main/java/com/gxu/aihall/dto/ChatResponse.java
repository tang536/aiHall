package com.gxu.aihall.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponse {
    private String answer;
    private List<String> sources;
    private Long responseTime;
    private String sessionId;
    private Long messageId;
}
