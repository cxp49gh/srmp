package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
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
            AiKnowledgeSearchRequest request = new AiKnowledgeSearchRequest();
            request.setTenantId(context == null ? null : context.getTenantId());
            request.setQuery(buildQuery(context, args));
            Object topK = context == null || context.getOptions() == null ? null : context.getOptions().get("topK");
            request.setTopK(toInt(topK, 5));
            if (args != null && args.get("filters") instanceof Map) {
                request.setFilters((Map<String, Object>) args.get("filters"));
            }
            AiKnowledgeSearchResponse response = aiKnowledgeRetrieverService.search(request);
            int count = response.getHits() == null ? 0 : response.getHits().size();
            return AiToolResult.success(name(), "知识库命中 " + count + " 条", response, count, System.currentTimeMillis() - start);
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
        if (context != null && context.getMapContext() != null && context.getMapContext().getMapObject() != null) {
            Map<String, Object> obj = context.getMapContext().getMapObject();
            append(sb, obj.get("diseaseName")); append(sb, obj.get("disease_name")); append(sb, obj.get("diseaseType")); append(sb, obj.get("disease_type")); append(sb, obj.get("severity"));
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
}
