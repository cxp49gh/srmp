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
    private Map<String, Object> metadata;
}
