package com.smartroad.srmp.agent.knowledge.service;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeStatsResponse;

public interface AiKnowledgeRetrieverService {
    AiKnowledgeSearchResponse search(AiKnowledgeSearchRequest request);

    AiKnowledgeStatsResponse stats(String tenantId);
}
