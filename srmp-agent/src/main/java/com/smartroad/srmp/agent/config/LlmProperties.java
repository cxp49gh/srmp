package com.smartroad.srmp.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "srmp.llm")
public class LlmProperties {
    private String provider = "openai-compatible";
    private String baseUrl;
    private String apiKey;
    private String model = "gpt-4o-mini";

    public boolean enabled() {
        return apiKey != null
                && apiKey.trim().length() > 0
                && !"your-api-key".equalsIgnoreCase(apiKey.trim());
    }
}
