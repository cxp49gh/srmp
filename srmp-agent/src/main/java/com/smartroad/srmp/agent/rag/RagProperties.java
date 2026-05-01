package com.smartroad.srmp.agent.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "srmp.ai.rag")
public class RagProperties {

    /**
     * 默认返回 topK。
     */
    private Integer topK = 8;

    /**
     * 分数阈值。0 表示不过滤。
     */
    private Double scoreThreshold = 0.0d;

    /**
     * Prompt 中最多拼接多少知识库字符。
     */
    private Integer maxContextChars = 6000;

    private Hybrid hybrid = new Hybrid();

    @Data
    public static class Hybrid {
        private Boolean enabled = true;
        private Integer vectorTopK = 10;
        private Integer keywordTopK = 10;
        private Double vectorWeight = 0.7d;
        private Double keywordWeight = 0.3d;
    }
}
