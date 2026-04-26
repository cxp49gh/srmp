package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateImportRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateQuery;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplateService;
import com.smartroad.srmp.agent.solution.template.TemplateVariableParser;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AiSolutionTemplateServiceImpl implements AiSolutionTemplateService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private TemplateVariableParser variableParser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> create(AiSolutionTemplateRequest request) {
        validateCreate(request);

        String tenantId = TenantContextHolder.getTenantId();
        String templateId = uuid();
        String versionId = uuid();

        String templateCode = safe(request.getTemplateCode(), codeOf(request.getTemplateName()));
        String version = safe(request.getVersion(), "v1");
        String sourceType = safe(request.getSourceType(), "LOCAL");
        String category = safe(request.getCategory(), "SOLUTION_TEMPLATE");
        String content = safe(request.getContent(), "");
        String hash = hash(content);
        List<String> variables = variableParser.parse(content);

        MapSqlParameterSource templateParams = new MapSqlParameterSource()
                .addValue("id", templateId)
                .addValue("tenantId", tenantId)
                .addValue("templateCode", templateCode)
                .addValue("templateName", request.getTemplateName())
                .addValue("solutionType", request.getSolutionType())
                .addValue("sourceType", sourceType)
                .addValue("sourceId", request.getSourceId())
                .addValue("category", category)
                .addValue("currentVersion", version)
                .addValue("status", "ENABLED");

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_template(" +
                        "id, tenant_id, template_code, template_name, solution_type, source_type, source_id, category, current_version, status, created_at, updated_at, deleted" +
                        ") values (" +
                        ":id, :tenantId, :templateCode, :templateName, :solutionType, :sourceType, :sourceId, :category, :currentVersion, :status, now(), now(), false" +
                        ")",
                templateParams
        );

        insertVersion(tenantId, versionId, templateId, version, content, hash, variables, request.getSourceUrl());

        Map<String, Object> result = detail(templateId);
        result.put("variables", variables);
        return result;
    }

    @Override
    public List<Map<String, Object>> list(AiSolutionTemplateQuery query) {
        String tenantId = TenantContextHolder.getTenantId();
        int limit = query == null || query.getLimit() == null ? 50 : query.getLimit();
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 200) {
            limit = 200;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("keyword", "%" + safe(query == null ? null : query.getKeyword(), "").toLowerCase() + "%")
                .addValue("solutionType", safe(query == null ? null : query.getSolutionType(), ""))
                .addValue("status", safe(query == null ? null : query.getStatus(), ""))
                .addValue("limit", limit);

        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, template_code, template_name, solution_type, source_type, source_id, category, current_version, status, created_at, updated_at " +
                        "from ai_solution_template " +
                        "where tenant_id=:tenantId and deleted=false " +
                        "and (:solutionType='' or solution_type=:solutionType) " +
                        "and (:status='' or status=:status) " +
                        "and (:keyword='%%' or lower(template_code) like :keyword or lower(template_name) like :keyword) " +
                        "order by updated_at desc limit :limit",
                params
        );
    }

    @Override
    public Map<String, Object> detail(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);

        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select t.id, t.tenant_id, t.template_code, t.template_name, t.solution_type, t.source_type, t.source_id, " +
                        "t.category, t.current_version, t.status, t.created_at, t.updated_at, v.content, v.variables, v.source_url " +
                        "from ai_solution_template t " +
                        "left join ai_solution_template_version v on v.tenant_id=t.tenant_id and v.template_id=t.id and v.version=t.current_version " +
                        "where t.tenant_id=:tenantId and t.id=:id and t.deleted=false",
                params
        );

        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    @Override
    public List<Map<String, Object>> versions(String templateId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("templateId", templateId);

        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, template_id, version, content_hash, variables, source_url, published_at, created_at " +
                        "from ai_solution_template_version " +
                        "where tenant_id=:tenantId and template_id=:templateId order by created_at desc",
                params
        );
    }

    @Override
    public Map<String, Object> importFromKnowledge(AiSolutionTemplateImportRequest request) {
        if (request == null || isBlank(request.getKnowledgeDocumentId())) {
            throw new IllegalArgumentException("knowledgeDocumentId 不能为空");
        }

        String tenantId = TenantContextHolder.getTenantId();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("documentId", request.getKnowledgeDocumentId());

        List<Map<String, Object>> docs = namedParameterJdbcTemplate.queryForList(
                "select d.id, d.title, d.source_type, d.source_id, d.url, " +
                        "coalesce(string_agg(c.content, E'\\n\\n' order by c.chunk_no), '') as content " +
                        "from knowledge_document d " +
                        "left join knowledge_chunk c on c.tenant_id=d.tenant_id and c.document_id=d.id and c.deleted=false " +
                        "where d.tenant_id=:tenantId and d.id=:documentId and d.deleted=false " +
                        "group by d.id, d.title, d.source_type, d.source_id, d.url",
                params
        );

        if (docs.isEmpty()) {
            throw new IllegalArgumentException("知识库文档不存在：" + request.getKnowledgeDocumentId());
        }

        Map<String, Object> doc = docs.get(0);
        String title = safe(request.getTemplateName(), String.valueOf(doc.get("title")));
        String content = String.valueOf(doc.get("content"));

        AiSolutionTemplateRequest createRequest = new AiSolutionTemplateRequest();
        createRequest.setTemplateCode(safe(request.getTemplateCode(), codeOf(title)));
        createRequest.setTemplateName(title);
        createRequest.setSolutionType(safe(request.getSolutionType(), "ROAD_ASSESSMENT_REPORT"));
        createRequest.setSourceType(String.valueOf(doc.get("source_type")));
        createRequest.setSourceId(String.valueOf(doc.get("id")));
        createRequest.setCategory("SOLUTION_TEMPLATE");
        createRequest.setVersion("v1");
        createRequest.setContent(content);
        createRequest.setSourceUrl(doc.get("url") == null ? null : String.valueOf(doc.get("url")));

        return create(createRequest);
    }

    @Override
    public Map<String, Object> disable(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);
        namedParameterJdbcTemplate.update(
                "update ai_solution_template set status='DISABLED', updated_at=now() where tenant_id=:tenantId and id=:id",
                params
        );
        return detail(id);
    }

    private void insertVersion(String tenantId,
                               String versionId,
                               String templateId,
                               String version,
                               String content,
                               String hash,
                               List<String> variables,
                               String sourceUrl) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", versionId)
                .addValue("tenantId", tenantId)
                .addValue("templateId", templateId)
                .addValue("version", version)
                .addValue("content", content)
                .addValue("contentHash", hash)
                .addValue("variables", toJson(variables))
                .addValue("sourceUrl", sourceUrl);

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_template_version(" +
                        "id, tenant_id, template_id, version, content, content_hash, variables, source_url, published_at, created_at" +
                        ") values (" +
                        ":id, :tenantId, :templateId, :version, :content, :contentHash, cast(:variables as jsonb), :sourceUrl, now(), now()" +
                        ")",
                params
        );
    }

    private void validateCreate(AiSolutionTemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (isBlank(request.getTemplateName())) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (isBlank(request.getSolutionType())) {
            throw new IllegalArgumentException("方案类型不能为空");
        }
        if (isBlank(request.getContent())) {
            throw new IllegalArgumentException("模板内容不能为空");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String codeOf(String text) {
        String value = safe(text, "solution_template");
        value = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        value = value.replaceAll("_+", "_");
        if (value.startsWith("_")) {
            value = value.substring(1);
        }
        if (value.endsWith("_")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.length() == 0) {
            value = "solution_template";
        }
        return value + "_" + System.currentTimeMillis();
    }

    private String hash(String content) {
        return DigestUtils.md5DigestAsHex(safe(content, "").getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
