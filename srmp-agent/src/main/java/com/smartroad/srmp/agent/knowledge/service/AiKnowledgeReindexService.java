package com.smartroad.srmp.agent.knowledge.service;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeReindexRequest;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeReindexResponse;

public interface AiKnowledgeReindexService {
    AiKnowledgeReindexResponse reindex(AiKnowledgeReindexRequest request);
}
