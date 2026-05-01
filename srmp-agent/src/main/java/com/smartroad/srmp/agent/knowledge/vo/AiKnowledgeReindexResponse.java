package com.smartroad.srmp.agent.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiKnowledgeReindexResponse {
    private Integer total = 0;
    private Integer success = 0;
    private Integer failed = 0;
    private Integer skipped = 0;

    private Boolean force = false;
    private String tenantId;
    private String sourceType;

    private String embeddingProvider;
    private String embeddingModel;
    private Integer embeddingDimensions;

    private Long costMs = 0L;
    private List<String> failedMessages = new ArrayList<>();
}
