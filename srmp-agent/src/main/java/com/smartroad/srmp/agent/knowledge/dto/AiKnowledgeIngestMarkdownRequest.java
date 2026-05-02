package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiKnowledgeIngestMarkdownRequest {
    private String tenantId;
    private String title;
    private String sourceType;
    private String sourceId;
    private String content;
    private String url;

    /**
     * true 时即使 sourceType + sourceId 对应内容未变化，也强制重建 chunk 和 embedding。
     */
    private Boolean force;

    private Map<String, Object> metadata;
}
