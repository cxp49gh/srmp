package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateImportRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateMatchPreviewRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateQuery;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRenderPreviewRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateStatusRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateVersionRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplateService;
import com.smartroad.srmp.agent.solution.template.MarkdownTemplateRenderer;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;
import com.smartroad.srmp.agent.solution.template.TemplateVariableParser;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Resource
    private AiSolutionTemplatePipelineService aiSolutionTemplatePipelineService;

    @Resource
    private MarkdownTemplateRenderer markdownTemplateRenderer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        String originType = safe(request.getOriginType(), defaultOriginTypeForSolution(request.getSolutionType()));
        String objectType = safe(request.getObjectType(), "ROAD_ROUTE");
        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        Integer priority = request.getPriority() == null ? 0 : request.getPriority();
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
                .addValue("status", "ENABLED")
                .addValue("originType", originType)
                .addValue("objectType", objectType)
                .addValue("isDefault", false)
                .addValue("priority", priority);

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_template(" +
                        "id, tenant_id, template_code, template_name, solution_type, source_type, source_id, category, current_version, status, " +
                        "origin_type, object_type, is_default, priority, created_at, updated_at, deleted" +
                        ") values (" +
                        ":id, :tenantId, :templateCode, :templateName, :solutionType, :sourceType, :sourceId, :category, :currentVersion, :status, " +
                        ":originType, :objectType, :isDefault, :priority, now(), now(), false" +
                        ")",
                templateParams
        );

        insertVersion(tenantId, versionId, templateId, version, content, hash, variables, request.getSourceUrl(), request.getChangeNote(), "");
        if (isDefault) {
            clearDefaultAndSet(tenantId, templateId, originType, objectType, request.getSolutionType());
        }

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
                .addValue("originType", safe(query == null ? null : query.getOriginType(), ""))
                .addValue("objectType", safe(query == null ? null : query.getObjectType(), ""))
                .addValue("isDefaultFilter", query != null && query.getIsDefault() != null)
                .addValue("isDefault", query != null && Boolean.TRUE.equals(query.getIsDefault()))
                .addValue("limit", limit);

        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, template_code, template_name, solution_type, source_type, source_id, category, current_version, status, " +
                        "origin_type, object_type, is_default, priority, created_at, updated_at " +
                        "from ai_solution_template " +
                        "where tenant_id=:tenantId and deleted=false " +
                        "and (:solutionType='' or solution_type=:solutionType) " +
                        "and (:status='' or status=:status) " +
                        "and (:originType='' or origin_type=:originType) " +
                        "and (:objectType='' or object_type=:objectType) " +
                        "and (:isDefaultFilter=false or is_default=:isDefault) " +
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
                        "t.category, t.current_version, t.status, t.origin_type, t.object_type, t.is_default, t.priority, " +
                        "t.created_at, t.updated_at, v.content, v.variables, v.source_url " +
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
                "select id, tenant_id, template_id, version, content_hash, variables, source_url, change_note, created_by, published_at, created_at " +
                        "from ai_solution_template_version " +
                        "where tenant_id=:tenantId and template_id=:templateId order by created_at desc",
                params
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFromKnowledge(AiSolutionTemplateImportRequest request) {
        if (request == null || isBlank(request.getKnowledgeDocumentId())) {
            throw new IllegalArgumentException("knowledgeDocumentId 不能为空");
        }

        String tenantId = TenantContextHolder.getTenantId();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("documentId", request.getKnowledgeDocumentId());

        List<Map<String, Object>> docs = namedParameterJdbcTemplate.queryForList(
                "select d.id, d.title, d.source_type, d.source_id, d.metadata ->> 'sourceUrl' as url, " +
                        "coalesce(string_agg(c.content, E'\\n\\n' order by c.chunk_index), '') as content " +
                        "from ai_knowledge_document d " +
                        "left join ai_knowledge_chunk c on c.tenant_id=d.tenant_id and c.document_id=d.id " +
                        "where d.tenant_id=:tenantId and d.id=:documentId and d.status='ACTIVE' " +
                        "group by d.id, d.title, d.source_type, d.source_id, d.metadata",
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
        String solutionType = safe(request.getSolutionType(), "SECTION_PLAN");
        createRequest.setSolutionType(solutionType);
        createRequest.setSourceType(String.valueOf(doc.get("source_type")));
        createRequest.setSourceId(String.valueOf(doc.get("id")));
        createRequest.setCategory("SOLUTION_TEMPLATE");
        createRequest.setVersion("v1");
        createRequest.setContent(content);
        createRequest.setSourceUrl(doc.get("url") == null ? null : String.valueOf(doc.get("url")));
        createRequest.setOriginType(defaultOriginTypeForSolution(solutionType));
        createRequest.setObjectType(defaultObjectTypeForSolution(solutionType));
        createRequest.setIsDefault(false);
        createRequest.setPriority(0);

        return create(createRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> disable(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);
        namedParameterJdbcTemplate.update(
                "update ai_solution_template set status='DISABLED', is_default=false, updated_at=now() where tenant_id=:tenantId and id=:id",
                params
        );
        return detail(id);
    }

    @Override
    public Map<String, Object> matchPreview(AiSolutionTemplateMatchPreviewRequest request) {
        SolutionTemplateContext context = new SolutionTemplateContext();
        context.setTenantId(TenantContextHolder.getTenantId());
        String solutionType = safe(request == null ? null : request.getSolutionType(), "SECTION_PLAN");
        context.setOriginType(safe(request == null ? null : request.getOriginType(), defaultOriginTypeForSolution(solutionType)));
        context.setObjectType(safe(request == null ? null : request.getObjectType(), defaultObjectTypeForSolution(solutionType)));
        context.setSolutionType(solutionType);
        context.setTemplateId(safe(request == null ? null : request.getTemplateId(), ""));
        context.setTemplateCode(safe(request == null ? null : request.getTemplateCode(), ""));
        TemplatePipelineResult result = aiSolutionTemplatePipelineService.generate(context);
        return result.getTemplateMeta();
    }

    @Override
    public Map<String, Object> renderPreview(String id, AiSolutionTemplateRenderPreviewRequest request) {
        Map<String, Object> template = requireDetail(id);
        String content = stringValue(template.get("content"));
        if (isBlank(content)) {
            throw new IllegalArgumentException("模板当前版本内容为空，无法预览");
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        if (request != null && request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        MarkdownTemplateRenderer.RenderResult renderResult = markdownTemplateRenderer.renderWithCheck(content, variables);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("renderedMarkdown", renderResult.getRenderedMarkdown());
        result.put("templateMeta", previewTemplateMeta(template, request, renderResult));
        result.put("variables", renderResult.getVariables());
        result.put("missingVariables", renderResult.getMissingVariables());
        result.put("unusedVariables", renderResult.getUnusedVariables());
        result.put("warnings", renderResult.getWarnings());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(String id, AiSolutionTemplateStatusRequest request) {
        String status = safe(request == null ? null : request.getStatus(), "").toUpperCase(Locale.ROOT);
        if (!Arrays.asList("ENABLED", "DISABLED").contains(status)) {
            throw new IllegalArgumentException("status 只支持 ENABLED / DISABLED");
        }
        namedParameterJdbcTemplate.update(
                "update ai_solution_template set status=:status, is_default=case when :status='DISABLED' then false else is_default end, updated_at=now() " +
                        "where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", TenantContextHolder.getTenantId())
                        .addValue("id", id)
                        .addValue("status", status)
        );
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setDefault(String id) {
        Map<String, Object> template = requireDetail(id);
        if (!"ENABLED".equalsIgnoreCase(stringValue(template.get("status")))) {
            throw new IllegalArgumentException("只有启用模板可以设为默认");
        }
        String tenantId = TenantContextHolder.getTenantId();
        String originType = stringValue(template.get("origin_type"));
        String objectType = stringValue(template.get("object_type"));
        String solutionType = stringValue(template.get("solution_type"));

        clearDefaultAndSet(tenantId, id, originType, objectType, solutionType);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createVersion(String id, AiSolutionTemplateVersionRequest request) {
        Map<String, Object> template = requireDetail(id);
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String version = safe(request.getVersion(), "");
        String content = safe(request.getContent(), "");
        if (isBlank(version)) {
            throw new IllegalArgumentException("版本号不能为空");
        }
        if (isBlank(content)) {
            throw new IllegalArgumentException("模板内容不能为空");
        }

        String tenantId = TenantContextHolder.getTenantId();
        List<String> variables = variableParser.parse(content);
        insertVersion(tenantId, uuid(), id, version, content, hash(content), variables, stringValue(template.get("source_url")), request.getChangeNote(), "");
        namedParameterJdbcTemplate.update(
                "update ai_solution_template set current_version=:version, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", id)
                        .addValue("version", version)
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
                               String sourceUrl,
                               String changeNote,
                               String createdBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", versionId)
                .addValue("tenantId", tenantId)
                .addValue("templateId", templateId)
                .addValue("version", version)
                .addValue("content", content)
                .addValue("contentHash", hash)
                .addValue("variables", toJson(variables))
                .addValue("sourceUrl", sourceUrl)
                .addValue("changeNote", changeNote)
                .addValue("createdBy", safe(createdBy, ""));

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_template_version(" +
                        "id, tenant_id, template_id, version, content, content_hash, variables, source_url, change_note, created_by, published_at, created_at" +
                        ") values (" +
                        ":id, :tenantId, :templateId, :version, :content, :contentHash, cast(:variables as jsonb), :sourceUrl, :changeNote, :createdBy, now(), now()" +
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

    private String defaultOriginTypeForSolution(String solutionType) {
        String type = safe(solutionType, "").toUpperCase(Locale.ROOT);
        if ("REGION_MAINTENANCE_SUGGESTION".equals(type)) {
            return "MAP_REGION";
        }
        if ("ROUTE_REPORT".equals(type) || "ROAD_ASSESSMENT_REPORT".equals(type)) {
            return "MAP_OBJECT";
        }
        return "MAP_OBJECT";
    }

    private String defaultObjectTypeForSolution(String solutionType) {
        String type = safe(solutionType, "").toUpperCase(Locale.ROOT);
        if ("SECTION_PLAN".equals(type) || "MAINTENANCE_SUGGESTION".equals(type)) {
            return "ROAD_SECTION";
        }
        if ("EVALUATION_UNIT_ADVICE".equals(type) || "LOW_SCORE_TREATMENT".equals(type) || "LOW_SCORE_SECTION_ANALYSIS".equals(type)) {
            return "ASSESSMENT_RESULT";
        }
        if ("DISEASE_REVIEW".equals(type) || "DISEASE_TREATMENT".equals(type) || "DISEASE_TREATMENT_PLAN".equals(type)) {
            return "DISEASE";
        }
        if ("REGION_MAINTENANCE_SUGGESTION".equals(type)) {
            return "MAP_REGION";
        }
        return "ROAD_ROUTE";
    }

    private void clearDefaultAndSet(String tenantId,
                                    String id,
                                    String originType,
                                    String objectType,
                                    String solutionType) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("originType", safe(originType, ""))
                .addValue("objectType", safe(objectType, ""))
                .addValue("solutionType", solutionType);
        namedParameterJdbcTemplate.update(
                "update ai_solution_template set is_default=false, updated_at=now() " +
                        "where tenant_id=:tenantId and deleted=false " +
                        "and coalesce(origin_type,'')=:originType " +
                        "and coalesce(object_type,'')=:objectType " +
                        "and solution_type=:solutionType",
                params
        );
        namedParameterJdbcTemplate.update(
                "update ai_solution_template set is_default=true, updated_at=now() where tenant_id=:tenantId and id=:id",
                params
        );
    }

    private Map<String, Object> previewTemplateMeta(Map<String, Object> template,
                                                    AiSolutionTemplateRenderPreviewRequest request,
                                                    MarkdownTemplateRenderer.RenderResult renderResult) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("matched", true);
        meta.put("fallback", false);
        meta.put("templateId", stringValue(template.get("id")));
        meta.put("templateCode", stringValue(template.get("template_code")));
        meta.put("templateName", stringValue(template.get("template_name")));
        meta.put("templateVersion", stringValue(template.get("current_version")));
        meta.put("solutionType", safe(request == null ? null : request.getSolutionType(), stringValue(template.get("solution_type"))));
        meta.put("objectType", safe(request == null ? null : request.getObjectType(), stringValue(template.get("object_type"))));
        meta.put("originType", safe(request == null ? null : request.getOriginType(), stringValue(template.get("origin_type"))));
        meta.put("missingVariables", renderResult.getMissingVariables());
        meta.put("unusedVariables", renderResult.getUnusedVariables());
        meta.put("warnings", renderResult.getWarnings());
        return meta;
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

    private Map<String, Object> requireDetail(String id) {
        Map<String, Object> template = detail(id);
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("模板不存在：" + id);
        }
        return template;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
