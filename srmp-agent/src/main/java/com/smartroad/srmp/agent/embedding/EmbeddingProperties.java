package com.smartroad.srmp.agent.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "srmp.ai.embedding")
public class EmbeddingProperties {
    /**
     * mock / local / openai-compatible
     */
    private String provider = "mock";
    private String endpoint;
    private String apiKey;
    private String model = "mock-hash-embedding";
    private Integer dimensions = 1536;
    private Integer batchSize = 16;

    public int safeDimensions() {
        return dimensions == null || dimensions <= 0 ? 1536 : dimensions;
    }
}
