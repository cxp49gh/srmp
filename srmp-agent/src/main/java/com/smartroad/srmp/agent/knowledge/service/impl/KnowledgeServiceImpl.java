package com.smartroad.srmp.agent.knowledge.service.impl;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeDocumentRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeIngestService;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Resource
    private AiKnowledgeIngestService aiKnowledgeIngestService;

    @Resource
    private AiKnowledgeRetrieverService aiKnowledgeRetrieverService;

    @Resource
    private LlmClient llmClient;

    @Override
    public String createDocument(KnowledgeDocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("知识文档请求不能为空");
        }
        AiKnowledgeIngestMarkdownRequest ingestRequest = new AiKnowledgeIngestMarkdownRequest();
        ingestRequest.setTenantId(TenantContextHolder.getTenantId());
        ingestRequest.setTitle(defaultString(request.getTitle(), "未命名文档"));
        ingestRequest.setSourceType(defaultString(request.getSourceType(), "LOCAL"));
        ingestRequest.setContent(defaultString(request.getContent(), ""));
        ingestRequest.setUrl(request.getUrl());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docType", defaultString(request.getDocType(), "MARKDOWN"));
        metadata.put("category", defaultString(request.getCategory(), "SYSTEM_MANUAL"));
        if (request.getUrl() != null) {
            metadata.put("url", request.getUrl());
            metadata.put("sourceUrl", request.getUrl());
        }
        ingestRequest.setMetadata(metadata);

        Map<String, Object> result = aiKnowledgeIngestService.ingestMarkdown(ingestRequest);
        return String.valueOf(result.get("documentId"));
    }

    @Override
    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        String query = request == null ? "" : defaultString(request.getQuery(), "");
        if (query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            AiKnowledgeSearchRequest aiRequest = new AiKnowledgeSearchRequest();
            aiRequest.setTenantId(TenantContextHolder.getTenantId());
            aiRequest.setQuery(query);
            aiRequest.setOriginalQuery(query);
            aiRequest.setTopK(normalizeTopK(request == null ? null : request.getTopK()));
            String sourceType = request == null ? null : request.getSourceType();
            if (sourceType != null && sourceType.trim().length() > 0) {
                aiRequest.setSourceTypes(Collections.singletonList(sourceType.trim()));
            }

            AiKnowledgeSearchResponse response = aiKnowledgeRetrieverService.search(aiRequest);
            List<AiKnowledgeSearchHit> hits = response == null ? Collections.emptyList() : response.getHits();
            if (hits == null || hits.isEmpty()) {
                return Collections.emptyList();
            }

            List<KnowledgeSearchResult> results = new ArrayList<>();
            for (AiKnowledgeSearchHit hit : hits) {
                results.add(mapAiHit(hit));
            }
            return results;
        } catch (Exception e) {
            log.warn("[KNOWLEDGE] ai_knowledge search failed, error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map ask(KnowledgeSearchRequest request) {
        List<KnowledgeSearchResult> results = search(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sources", results);

        String context = buildContext(results);
        String question = request == null ? "" : defaultString(request.getQuery(), "");

        String prompt = "请基于以下知识库片段回答问题。若资料不足，请明确说明。\n\n" +
                "【知识库片段】\n" + context + "\n\n" +
                "【用户问题】\n" + question;

        String answer = llmClient.chat("你是智路养护平台知识库问答助手，回答必须基于给定资料。", prompt);
        if (answer == null || answer.trim().isEmpty()) {
            answer = localAnswer(question, results);
        }

        data.put("answer", answer);
        return data;
    }

    private KnowledgeSearchResult mapAiHit(AiKnowledgeSearchHit hit) {
        KnowledgeSearchResult item = new KnowledgeSearchResult();
        item.setDocumentId(hit.getDocumentId());
        item.setChunkId(hit.getChunkId());
        item.setTitle(hit.getTitle());
        item.setHeading(hit.getSectionTitle());
        item.setContent(hit.getContent());
        item.setSourceType(hit.getSourceType());
        item.setScore(hit.getScore());
        item.setSourceUrl(metadataString(hit.getMetadata(), "sourceUrl", "url", "outlineUrl"));
        return item;
    }

    private String metadataString(Map<String, Object> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String buildContext(List<KnowledgeSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (KnowledgeSearchResult result : results) {
            sb.append("片段").append(index++).append("\n");
            sb.append("标题：").append(result.getTitle()).append("\n");
            sb.append("来源：").append(result.getSourceType()).append("\n");
            if (result.getSourceUrl() != null) {
                sb.append("链接：").append(result.getSourceUrl()).append("\n");
            }
            sb.append("内容：").append(result.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String localAnswer(String question, List<KnowledgeSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "知识库中暂未检索到与\"" + question + "\"相关的内容。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("根据知识库检索结果，相关资料如下：\n");
        for (int i = 0; i < results.size(); i++) {
            KnowledgeSearchResult item = results.get(i);
            sb.append(i + 1).append(". ").append(item.getTitle());
            if (item.getHeading() != null) {
                sb.append(" / ").append(item.getHeading());
            }
            sb.append("：").append(shortText(item.getContent(), 180)).append("\n");
        }
        return sb.toString();
    }

    private int normalizeTopK(Integer topK) {
        int value = topK == null ? 5 : topK;
        if (value <= 0) {
            value = 5;
        }
        if (value > 20) {
            value = 20;
        }
        return value;
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
