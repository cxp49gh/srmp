package com.smartroad.srmp.agent.knowledge.service.impl;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeDocumentRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.splitter.TextChunkSplitter;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private TextChunkSplitter textChunkSplitter;

    @Resource
    private LlmClient llmClient;

    /**
     * Phase37.4.1:
     * /api/knowledge/search 是早期知识库接口，查的是 knowledge_document / knowledge_chunk；
     * Phase36+ 新知识库查的是 ai_knowledge_document / ai_knowledge_chunk。
     *
     * 为了兼容旧前端和旧接口，当 legacy search 为空时，自动兜底到新的 AI 向量知识库。
     */
    @Resource
    private AiKnowledgeRetrieverService aiKnowledgeRetrieverService;

    @Override
    public String createDocument(KnowledgeDocumentRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        String documentId = uuid();
        String sourceType = defaultString(request.getSourceType(), "LOCAL");
        String docType = defaultString(request.getDocType(), "MARKDOWN");
        String content = defaultString(request.getContent(), "");
        String contentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));

        MapSqlParameterSource docParams = new MapSqlParameterSource();
        docParams.addValue("id", documentId);
        docParams.addValue("tenantId", tenantId);
        docParams.addValue("sourceType", sourceType);
        docParams.addValue("sourceId", null);
        docParams.addValue("title", defaultString(request.getTitle(), "未命名文档"));
        docParams.addValue("docType", docType);
        docParams.addValue("category", defaultString(request.getCategory(), "SYSTEM_MANUAL"));
        docParams.addValue("contentHash", contentHash);
        docParams.addValue("url", request.getUrl());

        namedParameterJdbcTemplate.update(
                "insert into knowledge_document(" +
                        "id, tenant_id, source_type, source_id, title, doc_type, category, content_hash, url, status, created_at, updated_at, deleted" +
                        ") values (" +
                        ":id, :tenantId, :sourceType, :sourceId, :title, :docType, :category, :contentHash, :url, 'ENABLED', now(), now(), false" +
                        ")",
                docParams
        );

        List<String> chunks = textChunkSplitter.split(content);
        int chunkNo = 1;
        for (String chunk : chunks) {
            MapSqlParameterSource chunkParams = new MapSqlParameterSource();
            chunkParams.addValue("id", uuid());
            chunkParams.addValue("tenantId", tenantId);
            chunkParams.addValue("documentId", documentId);
            chunkParams.addValue("chunkNo", chunkNo++);
            chunkParams.addValue("heading", extractHeading(chunk));
            chunkParams.addValue("content", chunk);
            chunkParams.addValue("contentTokens", Math.max(1, chunk.length() / 2));
            chunkParams.addValue("sourceType", sourceType);
            chunkParams.addValue("sourceUrl", request.getUrl());

            namedParameterJdbcTemplate.update(
                    "insert into knowledge_chunk(" +
                            "id, tenant_id, document_id, chunk_no, heading, content, content_tokens, source_type, source_url, metadata, created_at, updated_at, deleted" +
                            ") values (" +
                            ":id, :tenantId, :documentId, :chunkNo, :heading, :content, :contentTokens, :sourceType, :sourceUrl, '{}'::jsonb, now(), now(), false" +
                            ")",
                    chunkParams
            );
        }

        return documentId;
    }

    @Override
    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        String query = request == null ? "" : defaultString(request.getQuery(), "");
        if (query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeSearchResult> legacyResults = legacySearch(request);
        if (!legacyResults.isEmpty()) {
            return legacyResults;
        }

        List<KnowledgeSearchResult> aiResults = searchAiKnowledge(request);
        if (!aiResults.isEmpty()) {
            return aiResults;
        }

        return Collections.emptyList();
    }

    private List<KnowledgeSearchResult> legacySearch(KnowledgeSearchRequest request) {
        String query = request == null ? "" : defaultString(request.getQuery(), "");
        int topK = normalizeTopK(request == null ? null : request.getTopK());

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", TenantContextHolder.getTenantId());
        params.addValue("category", defaultString(request.getCategory(), ""));
        params.addValue("sourceType", defaultString(request.getSourceType(), ""));
        params.addValue("limit", topK);

        List<String> tokens = keywordTokens(query);
        String keywordSql;
        if (tokens.isEmpty()) {
            params.addValue("kw0", "%" + query.toLowerCase(Locale.ROOT) + "%");
            keywordSql = " and (lower(c.content) like :kw0 or lower(coalesce(c.heading,'')) like :kw0 or lower(d.title) like :kw0) ";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(" and (");
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) {
                    sb.append(" or ");
                }
                String key = "kw" + i;
                params.addValue(key, "%" + tokens.get(i).toLowerCase(Locale.ROOT) + "%");
                sb.append("lower(c.content) like :").append(key)
                        .append(" or lower(coalesce(c.heading,'')) like :").append(key)
                        .append(" or lower(d.title) like :").append(key);
            }
            sb.append(") ");
            keywordSql = sb.toString();
        }

        String sql =
                "select d.id as document_id, c.id as chunk_id, d.title, c.heading, c.content, c.source_type, c.source_url, " +
                        " case when lower(coalesce(c.heading,'')) like :kw0 then 2.0 else 1.0 end as score " +
                        " from knowledge_chunk c " +
                        " join knowledge_document d on d.id=c.document_id and d.tenant_id=c.tenant_id and d.deleted=false " +
                        " where c.tenant_id=:tenantId and c.deleted=false " +
                        "   and (:category='' or d.category=:category) " +
                        "   and (:sourceType='' or c.source_type=:sourceType) " +
                        keywordSql +
                        " order by score desc, c.updated_at desc " +
                        " limit :limit";

        if (!params.hasValue("kw0")) {
            params.addValue("kw0", "%" + tokens.get(0).toLowerCase(Locale.ROOT) + "%");
        }

        try {
            return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> mapResult(rs));
        } catch (Exception e) {
            log.warn("[KNOWLEDGE] legacy knowledge search failed, fallback to ai_knowledge, error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KnowledgeSearchResult> searchAiKnowledge(KnowledgeSearchRequest request) {
        try {
            AiKnowledgeSearchRequest aiRequest = new AiKnowledgeSearchRequest();
            aiRequest.setTenantId(TenantContextHolder.getTenantId());
            aiRequest.setQuery(request.getQuery());
            aiRequest.setOriginalQuery(request.getQuery());
            aiRequest.setTopK(normalizeTopK(request.getTopK()));
            if (request.getSourceType() != null && request.getSourceType().trim().length() > 0) {
                aiRequest.setSourceTypes(Collections.singletonList(request.getSourceType().trim()));
            }

            AiKnowledgeSearchResponse response = aiKnowledgeRetrieverService.search(aiRequest);
            List<AiKnowledgeSearchHit> hits = response == null ? Collections.emptyList() : response.getHits();
            if (hits == null || hits.isEmpty()) {
                return Collections.emptyList();
            }

            List<KnowledgeSearchResult> results = new ArrayList<>();
            for (AiKnowledgeSearchHit hit : hits) {
                KnowledgeSearchResult item = new KnowledgeSearchResult();
                item.setDocumentId(hit.getDocumentId());
                item.setChunkId(hit.getChunkId());
                item.setTitle(hit.getTitle());
                item.setHeading(hit.getSectionTitle());
                item.setContent(hit.getContent());
                item.setSourceType(hit.getSourceType());
                item.setScore(hit.getScore());
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("[KNOWLEDGE] ai_knowledge fallback search failed, error={}", e.getMessage());
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

    private KnowledgeSearchResult mapResult(ResultSet rs) throws SQLException {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setDocumentId(rs.getString("document_id"));
        result.setChunkId(rs.getString("chunk_id"));
        result.setTitle(rs.getString("title"));
        result.setHeading(rs.getString("heading"));
        result.setContent(rs.getString("content"));
        result.setSourceType(rs.getString("source_type"));
        result.setSourceUrl(rs.getString("source_url"));
        result.setScore(rs.getDouble("score"));
        return result;
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

    private List<String> keywordTokens(String query) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String text = query == null ? "" : query.trim();
        String normalized = text
                .replace("？", " ")
                .replace("?", " ")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace(".", " ")
                .replace("是什么意思", " ")
                .replace("什么是", " ")
                .replace("含义", " ");

        for (String part : normalized.split("\\s+")) {
            String token = part.trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }

        // 中文短句兜底：对常见指标类问题补充核心词。
        String upper = text.toUpperCase(Locale.ROOT);
        for (String indicator : Arrays.asList("MQI", "PQI", "PCI", "RQI", "RDI", "SRI", "PSSI")) {
            if (upper.contains(indicator)) {
                tokens.add(indicator.toLowerCase(Locale.ROOT));
            }
        }
        if (text.contains("指标")) {
            tokens.add("指标");
        }

        if (tokens.size() > 12) {
            return new ArrayList<>(tokens).subList(0, 12);
        }
        return new ArrayList<>(tokens);
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String extractHeading(String chunk) {
        if (chunk == null) {
            return null;
        }
        String[] lines = chunk.split("\n");
        for (String line : lines) {
            String text = line.trim();
            if (text.startsWith("#")) {
                return text.replace("#", "").trim();
            }
        }
        return null;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
