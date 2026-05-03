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

    /** 建立连接超时，单位毫秒。 */
    private Integer connectTimeoutMs = 15000;

    /** 等待模型响应超时，单位毫秒。区域养护方案 Prompt 较长，默认提高到 180 秒。 */
    private Integer readTimeoutMs = 180000;

    /** curl 兜底调用最大耗时，单位秒。 */
    private Integer curlMaxTimeSeconds = 180;

    /** OpenAI-compatible chat/completions max_tokens。 */
    private Integer maxTokens = 1800;

    /** OpenAI-compatible temperature。 */
    private Double temperature = 0.2d;

    public boolean enabled() {
        return apiKey != null
                && apiKey.trim().length() > 0
                && !"your-api-key".equalsIgnoreCase(apiKey.trim());
    }
}
