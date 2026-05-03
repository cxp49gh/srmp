package com.smartroad.srmp.agent.outline.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "srmp.outline")
public class OutlineProperties {
    private Boolean enabled = false;
    private String baseUrl;
    private String apiToken;
    private Boolean syncEnabled = false;
    private String defaultCollectionId;
    private Integer searchLimit = 5;

    /** Outline API 建立连接超时，单位毫秒。 */
    private Integer connectTimeoutMs = 10000;

    /** Outline API 读取响应超时，单位毫秒。 */
    private Integer readTimeoutMs = 60000;

    public boolean usable() {
        return Boolean.TRUE.equals(enabled)
                && baseUrl != null && baseUrl.trim().length() > 0
                && apiToken != null && apiToken.trim().length() > 0
                && !"your-outline-token".equalsIgnoreCase(apiToken.trim());
    }
}
