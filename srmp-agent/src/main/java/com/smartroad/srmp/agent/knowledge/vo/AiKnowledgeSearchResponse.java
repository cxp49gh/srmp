package com.smartroad.srmp.agent.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiKnowledgeSearchResponse {
    private String query;
    private String originalQuery;
    private String rewrittenQuery;

    private List<AiKnowledgeSearchHit> hits = new ArrayList<>();

    /**
     * VECTOR / HYBRID / KEYWORD / KEYWORD_FALLBACK / NO_DATA
     */
    private String searchMode = "NO_DATA";

    /**
     * VECTOR / HYBRID / KEYWORD_FALLBACK
     */
    private String retrievalStrategy = "NO_DATA";

    /**
     * true 表示本次查询确实使用了向量相似度检索。
     */
    private Boolean vectorUsed = false;

    private Boolean fallback = false;
    private String fallbackReason;

    private Boolean hybridEnabled = false;
    private Double scoreThreshold = 0.0d;

    private String embeddingProvider;
    private String embeddingModel;
    private Integer embeddingDimensions;

    private Integer hitCount = 0;
    private Double topScore;
}
