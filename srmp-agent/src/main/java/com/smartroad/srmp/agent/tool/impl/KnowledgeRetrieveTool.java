package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.rag.RagQueryRewriteService;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class KnowledgeRetrieveTool implements AiTool {

    @Resource
    private AiKnowledgeRetrieverService aiKnowledgeRetrieverService;

    @Resource
    private RagQueryRewriteService ragQueryRewriteService;

    @Override
    public String name() { return "knowledge.retrieve"; }
    @Override
    public String description() { return "检索向量知识库，返回专业资料片段"; }
    @Override
    public boolean supports(AiToolContext context) { return true; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            String rawQuery = buildQuery(context, args);
            String rewrittenQuery = ragQueryRewriteService.rewrite(rawQuery, context == null ? null : context.getMapContext());

            AiKnowledgeSearchRequest request = new AiKnowledgeSearchRequest();
            request.setTenantId(context == null ? null : context.getTenantId());
            request.setOriginalQuery(rawQuery);
            request.setQuery(rewrittenQuery);
            request.setRewrittenQuery(rewrittenQuery);

            Object topK = context == null || context.getOptions() == null ? null : context.getOptions().get("topK");
            request.setTopK(toInt(topK, 8));

            if (args != null && args.get("filters") instanceof Map) {
                request.setFilters((Map<String, Object>) args.get("filters"));
            }

            AiKnowledgeSearchResponse response = aiKnowledgeRetrieverService.search(request);
            response.setRequest(requestSummary(request));
            int count = response.getHits() == null ? 0 : response.getHits().size();

            String summary = "知识库命中 " + count + " 条";
            if (response.getRetrievalStrategy() != null) summary += "，策略：" + response.getRetrievalStrategy();
            if (response.getSearchMode() != null) summary += "，模式：" + response.getSearchMode();
            if (Boolean.TRUE.equals(response.getVectorUsed())) summary += "，已使用向量检索";
            if (Boolean.TRUE.equals(response.getFallback())) summary += "，已降级";

            return AiToolResult.success(name(), summary, response, count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return AiToolResult.failed(name(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String buildQuery(AiToolContext context, Map<String, Object> args) {
        if (args != null && args.get("query") != null) {
            return String.valueOf(args.get("query"));
        }

        StringBuilder sb = new StringBuilder();
        if (context != null && context.getUserQuestion() != null) {
            sb.append(context.getUserQuestion());
        }

        if (context != null && context.getMapContext() != null) {
            append(sb, context.getMapContext().getRouteCode());
            append(sb, context.getMapContext().getYear());

            if (context.getMapContext().getMapObject() != null) {
                Map<String, Object> obj = context.getMapContext().getMapObject();
                append(sb, obj.get("objectType"));
                append(sb, obj.get("object_type"));
                append(sb, obj.get("routeCode"));
                append(sb, obj.get("route_code"));
                append(sb, obj.get("diseaseName"));
                append(sb, obj.get("disease_name"));
                append(sb, obj.get("diseaseType"));
                append(sb, obj.get("disease_type"));
                append(sb, obj.get("severity"));
            }
        }

        return sb.toString().trim();
    }

    private void append(StringBuilder sb, Object value) {
        if (value != null && String.valueOf(value).trim().length() > 0) {
            sb.append(' ').append(value);
        }
    }

    private Integer toInt(Object value, int defaultValue) {
        try { return value == null ? defaultValue : Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    private Map<String, Object> requestSummary(AiKnowledgeSearchRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tenantId", request.getTenantId());
        summary.put("query", request.getOriginalQuery());
        summary.put("rewrittenQuery", request.getRewrittenQuery());
        summary.put("topK", request.getTopK());
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            summary.put("filters", request.getFilters());
        }
        if (request.getSourceTypes() != null && !request.getSourceTypes().isEmpty()) {
            summary.put("sourceTypes", request.getSourceTypes());
        }
        return summary;
    }
}
