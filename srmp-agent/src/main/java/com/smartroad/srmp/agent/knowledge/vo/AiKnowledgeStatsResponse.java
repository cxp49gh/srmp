package com.smartroad.srmp.agent.knowledge.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiKnowledgeStatsResponse {
    private Long documentCount = 0L;
    private Long chunkCount = 0L;
    private Long embeddedChunkCount = 0L;
    private Map<String, Long> sourceTypes = new LinkedHashMap<>();
    /**
     * key: provider:model:dimensions，例如 local:bge-m3:1024。
     */
    private Map<String, Long> chunkEmbeddingProviders = new LinkedHashMap<>();

    private String embeddingProvider;
    private String embeddingModel;
    private Integer embeddingDimensions;
    private Boolean vectorEnabled = false;

    private String status;
    private String message;
}
