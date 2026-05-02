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

    /**
     * 是否在入库时同步生成向量。默认 true。
     */
    private Boolean vectorize = true;

    /**
     * 向量化失败时是否让整篇文档入库失败。默认 true。
     * 手工导入默认严格失败；Outline 同步可设为 false，先保留文档和切片，再记录向量化失败。
     */
    private Boolean failOnEmbeddingError = true;

    private Map<String, Object> metadata;
}
