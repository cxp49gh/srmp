package com.smartroad.srmp.agent.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionDraftService;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SolutionSaveTool implements AiTool {

    @Resource
    private AiSolutionDraftService aiSolutionDraftService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() { return "solution.saveTask"; }

    @Override
    public String description() { return "保存已确认的方案草稿任务。"; }

    @Override
    public boolean supports(AiToolContext context) { return true; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeArgs = args == null ? new LinkedHashMap<String, Object>() : args;
        if (!Boolean.TRUE.equals(safeArgs.get("confirmed"))) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "NEEDS_CONFIRMATION");
            data.put("message", "保存方案任务需要 confirmed=true");
            return AiToolResult.success(name(), "保存前需要确认", data, 0, System.currentTimeMillis() - start);
        }
        return saveDraft(context, safeArgs, start);
    }

    private AiToolResult saveDraft(AiToolContext context, Map<String, Object> args, long start) {
        Map<String, Object> payload = new LinkedHashMap<>(args);
        payload.remove("confirmed");
        Map<String, Object> mapContext = mapValue(payload.remove("mapContext"));
        AiSolutionDraftSaveRequest request = objectMapper.convertValue(payload, AiSolutionDraftSaveRequest.class);
        if (isBlank(request.getOriginType())) {
            request.setOriginType(request.getRegionSummary() == null || request.getRegionSummary().isEmpty() ? "MAP_OBJECT" : "MAP_REGION");
        }
        if (request.getMapObject() == null || request.getMapObject().isEmpty()) {
            request.setMapObject(defaultMapObject(request, mapContext, context));
        }
        if (request.getRouteCode() == null && mapContext.get("routeCode") != null) {
            request.setRouteCode(String.valueOf(mapContext.get("routeCode")));
        }
        if (request.getYear() == null && mapContext.get("year") != null) {
            request.setYear(intValue(mapContext.get("year")));
        }
        Map<String, Object> saved = "MAP_REGION".equalsIgnoreCase(request.getOriginType())
                ? aiSolutionDraftService.saveMapRegionDraft(request)
                : aiSolutionDraftService.saveMapObjectDraft(request);
        return AiToolResult.success(name(), "方案任务已保存", saved, 1, System.currentTimeMillis() - start);
    }

    private Map<String, Object> defaultMapObject(AiSolutionDraftSaveRequest request, Map<String, Object> mapContext, AiToolContext context) {
        Map<String, Object> mapObject = new LinkedHashMap<>();
        if ("MAP_REGION".equalsIgnoreCase(request.getOriginType())) {
            mapObject.put("objectType", "MAP_REGION");
            if (mapContext.get("geometry") != null) {
                mapObject.put("geometry", mapContext.get("geometry"));
            }
            if (request.getRegionSummary() != null) {
                mapObject.put("regionSummary", request.getRegionSummary());
            }
            if (request.getTrace() != null && request.getTrace().get("traceId") != null) {
                mapObject.put("id", request.getTrace().get("traceId"));
            }
        } else if (context != null && context.getMapContext() != null && context.getMapContext().getMapObject() != null) {
            mapObject.putAll(context.getMapContext().getMapObject());
        }
        return mapObject;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private Integer intValue(Object value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
