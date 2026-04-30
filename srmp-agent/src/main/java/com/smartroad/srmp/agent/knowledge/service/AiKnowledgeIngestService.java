package com.smartroad.srmp.agent.knowledge.service;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;

import java.util.Map;

public interface AiKnowledgeIngestService {
    Map<String, Object> ingestMarkdown(AiKnowledgeIngestMarkdownRequest request);
}
