package com.smartroad.srmp.agent.knowledge.service;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeFeedbackCreateRequest;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeFeedbackQuery;

import java.util.List;
import java.util.Map;

public interface AiKnowledgeFeedbackService {
    Map<String, Object> create(AiKnowledgeFeedbackCreateRequest request);

    List<Map<String, Object>> list(AiKnowledgeFeedbackQuery query);
}
