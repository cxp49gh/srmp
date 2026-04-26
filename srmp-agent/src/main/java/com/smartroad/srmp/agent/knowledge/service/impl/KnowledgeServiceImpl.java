package com.smartroad.srmp.agent.knowledge.service.impl;

import com.smartroad.srmp.agent.knowledge.dto.KnowledgeDocumentRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.splitter.TextChunkSplitter;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private TextChunkSplitter textChunkSplitter;

    @Resource
    private LlmClient llmClient;

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

        int topK = request.getTopK() == null ? 5 : request.getTopK();
        if (topK <= 0) {
            topK = 5;
        }
        if (topK > 20) {
            topK = 20;
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", TenantContextHolder.getTenantId());
        params.addValue("kw", "%" + query.toLowerCase() + "%");
        params.addValue("category", defaultString(request.getCategory(), ""));
        params.addValue("sourceType", defaultString(request.getSourceType(), ""));
        params.addValue("limit", topK);

        String sql =
                "select d.id as document_id, c.id as chunk_id, d.title, c.heading, c.content, c.source_type, c.source_url, " +
                        " case when lower(c.heading) like :kw then 2.0 else 1.0 end as score " +
                " from knowledge_chunk c " +
                " join knowledge_document d on d.id=c.document_id and d.tenant_id=c.tenant_id and d.deleted=false " +
                " where c.tenant_id=:tenantId and c.deleted=false " +
                "   and (:category='' or d.category=:category) " +
                "   and (:sourceType='' or c.source_type=:sourceType) " +
                "   and (lower(c.content) like :kw or lower(coalesce(c.heading,'')) like :kw or lower(d.title) like :kw) " +
                " order by score desc, c.updated_at desc " +
                " limit :limit";

        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> mapResult(rs));
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
