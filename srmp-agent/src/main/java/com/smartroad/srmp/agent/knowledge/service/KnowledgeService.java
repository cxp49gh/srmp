package com.smartroad.srmp.agent.knowledge.service;

import com.smartroad.srmp.agent.knowledge.dto.KnowledgeDocumentRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;

import java.util.List;
import java.util.Map;

public interface KnowledgeService {
    String createDocument(KnowledgeDocumentRequest request);

    List<KnowledgeSearchResult> search(KnowledgeSearchRequest request);

    Map ask(KnowledgeSearchRequest request);
}
