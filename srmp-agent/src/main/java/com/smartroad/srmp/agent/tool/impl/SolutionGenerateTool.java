package com.smartroad.srmp.agent.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionService;
import com.smartroad.srmp.agent.solution.service.AiMapRegionSolutionGateway;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SolutionGenerateTool implements AiTool {

    @Resource
    private ObjectProvider<AiMapRegionSolutionGateway> mapRegionSolutionGatewayProvider;

    @Resource
    private MapObjectSolutionService mapObjectSolutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() { return "solution.generateDraft"; }

    @Override
    public String description() { return "基于当前地图对象、路线或区域生成方案草稿预览。"; }

    @Override
    public boolean supports(AiToolContext context) { return true; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeArgs = args == null ? new LinkedHashMap<String, Object>() : args;
        String action = stringValue(safeArgs.get("action"));
        Map<String, Object> mapContext = mapContextMap(safeArgs, context);
        if ("GENERATE_REGION_SOLUTION".equals(action)) {
            AiMapRegionSolutionGateway gateway = mapRegionSolutionGatewayProvider.getIfAvailable();
            if (gateway == null) {
                return AiToolResult.failed(name(), "区域方案生成服务未注册", System.currentTimeMillis() - start);
            }
            Map<String, Object> data = gateway.generate(regionRequestMap(safeArgs, mapContext, context));
            return AiToolResult.success(name(), "区域养护建议预览已生成", data, 1, System.currentTimeMillis() - start);
        }
        if ("GENERATE_OBJECT_SOLUTION".equals(action)) {
            MapObjectSolutionResponse response = mapObjectSolutionService.generate(objectSolutionRequest(safeArgs, mapContext, context, "GENERAL_ADVICE", null));
            Map<String, Object> data = objectMapper.convertValue(response, Map.class);
            return AiToolResult.success(name(), "对象方案预览已生成", data, 1, System.currentTimeMillis() - start);
        }
        if ("GENERATE_ROUTE_REPORT".equals(action)) {
            MapObjectSolutionResponse response = mapObjectSolutionService.generate(objectSolutionRequest(safeArgs, mapContext, context, "ROUTE_REPORT", "ROAD_ROUTE"));
            Map<String, Object> data = objectMapper.convertValue(response, Map.class);
            return AiToolResult.success(name(), "路线报告预览已生成", data, 1, System.currentTimeMillis() - start);
        }
        return AiToolResult.failed(name(), "不支持的方案生成动作：" + action, System.currentTimeMillis() - start);
    }

    private MapObjectSolutionRequest objectSolutionRequest(Map<String, Object> args,
                                                           Map<String, Object> mapContext,
                                                           AiToolContext context,
                                                           String defaultSolutionType,
                                                           String forcedObjectType) {
        Map<String, Object> mapObject = mapValue(firstNonNull(args.get("mapObject"), mapContext.get("mapObject")));
        String objectType = firstNonBlank(forcedObjectType, stringValue(args.get("objectType")), stringValue(mapObject.get("objectType")), stringValue(mapObject.get("object_type")));
        String routeCode = firstNonBlank(stringValue(args.get("routeCode")), stringValue(mapContext.get("routeCode")), stringValue(mapContext.get("route_code")), stringValue(mapObject.get("routeCode")), stringValue(mapObject.get("route_code")));
        Integer year = intValue(firstNonNull(args.get("year"), mapContext.get("year"), mapObject.get("year")));
        if (mapObject.isEmpty() && "ROAD_ROUTE".equals(objectType)) {
            putIfPresent(mapObject, "objectType", objectType);
            putIfPresent(mapObject, "routeCode", routeCode);
            putIfPresent(mapObject, "year", year);
        }
        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setTenantId(context == null ? null : context.getTenantId());
        request.setObjectType(objectType);
        request.setObjectId(firstNonBlank(stringValue(args.get("objectId")), stringValue(mapObject.get("objectId")), stringValue(mapObject.get("object_id")), stringValue(mapObject.get("id"))));
        request.setRouteCode(routeCode);
        request.setYear(year);
        request.setSolutionType(firstNonBlank(stringValue(args.get("solutionType")), defaultSolutionType));
        request.setMapObject(mapObject);
        request.setOptions(mergeOptions(args, context));
        return request;
    }

    private Map<String, Object> regionRequestMap(Map<String, Object> args, Map<String, Object> mapContext, AiToolContext context) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("solutionType", firstNonBlank(stringValue(args.get("solutionType")), "REGION_MAINTENANCE_SUGGESTION"));
        request.put("geometry", firstNonNull(args.get("geometry"), mapContext.get("geometry")));
        request.put("query", firstNonNull(args.get("query"), regionQuery(mapContext)));
        request.put("layers", firstNonNull(args.get("layers"), mapContext.get("selectedLayers")));
        request.put("options", mergeOptions(args, context));
        return request;
    }

    private Map<String, Object> regionQuery(Map<String, Object> mapContext) {
        Map<String, Object> query = new LinkedHashMap<>();
        putIfPresent(query, "routeCode", firstNonNull(mapContext.get("routeCode"), mapContext.get("route_code")));
        putIfPresent(query, "year", mapContext.get("year"));
        Map<String, Object> extra = mapValue(mapContext.get("extra"));
        putIfPresent(query, "indexCode", extra.get("indexCode"));
        putIfPresent(query, "grade", extra.get("grade"));
        return query;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapContextMap(Map<String, Object> args, AiToolContext context) {
        Object raw = args.get("mapContext");
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        if (context != null && context.getMapContext() != null) {
            return objectMapper.convertValue(context.getMapContext(), Map.class);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private Map<String, Object> mergeOptions(Map<String, Object> args, AiToolContext context) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (context != null && context.getOptions() != null) {
            options.putAll(context.getOptions());
        }
        options.putAll(mapValue(args.get("options")));
        return options;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
