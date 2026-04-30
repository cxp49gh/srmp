package com.smartroad.srmp.agent.knowledge.service;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;

public interface AiKnowledgeRetrieverService {
    AiKnowledgeSearchResponse search(AiKnowledgeSearchRequest request);
}
