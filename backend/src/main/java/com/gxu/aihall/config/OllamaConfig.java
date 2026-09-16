package com.gxu.aihall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaConfig {
    private String baseUrl = "http://localhost:11434";
    private String model = "deepseek-r1:7b";
    private Integer timeout = 120;
    private Double temperature = 0.7;
    private Double topP = 0.9;
    /** 上下文窗口长度（token），越小越省显存、越快 */
    private Integer numCtx = 4096;
    /** 单次最大生成 token 数，避免思维链/回答无限生成；deepseek-r1 思维链较长，需留足空间 */
    private Integer numPredict = 2048;
    /** 模型在显存/内存中的常驻时间，避免每次冷加载（如 30m、-1 表示永久常驻） */
    private String keepAlive = "30m";
}
