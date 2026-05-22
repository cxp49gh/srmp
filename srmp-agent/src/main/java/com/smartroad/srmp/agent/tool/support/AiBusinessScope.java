package com.smartroad.srmp.agent.tool.support;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiBusinessScope {
    private String tenantId;
    private String projectId;
    private String routeCode;
    private Integer year;
    private String sectionTier;
    private String contextScope;
    private String objectType;
    private String objectId;
    private String assessmentObjectType;
    private String direction;
    private Double startStake;
    private Double endStake;
    private Object bbox;
    private String geometryType;
    private List<String> selectedLayers = new ArrayList<>();
    private List<String> scopeWarnings = new ArrayList<>();

    public static AiBusinessScope from(AiToolContext context, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null ? Collections.<String, Object>emptyMap() : args;
        MapAiContext mapContext = context == null ? null : context.getMapContext();
        Map<String, Object> extra = asMap(mapContext == null ? null : mapContext.getExtra());
        Map<String, Object> rawContext = asMap(first(extra, "rawContext", "raw_context"));
        Map<String, Object> query = asMap(first(rawContext, "query"));
        Map<String, Object> selected = asMap(first(rawContext, "selected", "selectedMapObject", "mapObject"));
        Map<String, Object> mapObject = asMap(mapContext == null ? null : mapContext.getMapObject());
        Map<String, Object> rawObject = asMap(first(mapObject, "raw"));
        Map<String, Object> viewport = asMap(mapContext == null ? null : mapContext.getViewport());
        Map<String, Object> geometry = asMap(mapContext == null ? null : mapContext.getGeometry());
        boolean objectMode = "OBJECT".equalsIgnoreCase(stringValue(mapContext == null ? null : mapContext.getMode()))
                || !mapObject.isEmpty();
        Object objectRoute = first(mapObject, "routeCode", "route_code", "route");
        Object selectedRoute = first(selected, "routeCode", "route_code", "route");
        Object rawObjectRoute = first(rawObject, "routeCode", "route_code", "route");
        Object argRoute = first(safeArgs, "routeCode", "route_code", "route");
        Object contextRoute = mapContext == null ? null : mapContext.getRouteCode();

        AiBusinessScope scope = new AiBusinessScope();
        scope.tenantId = stringValue(
                first(safeArgs, "tenantId", "tenant_id"),
                context == null ? null : context.getTenantId(),
                mapContext == null ? null : mapContext.getTenantId()
        );
        scope.projectId = stringValue(
                first(safeArgs, "projectId", "project_id"),
                first(extra, "projectId", "project_id"),
                first(query, "projectId", "project_id"),
                first(rawContext, "projectId", "project_id")
        );
        scope.routeCode = objectMode
                ? stringValue(objectRoute, selectedRoute, rawObjectRoute, argRoute, contextRoute)
                : stringValue(argRoute, contextRoute, objectRoute, selectedRoute, rawObjectRoute);
        scope.year = integerValue(
                first(safeArgs, "year"),
                mapContext == null ? null : mapContext.getYear(),
                first(query, "year"),
                first(mapObject, "year"),
                first(rawObject, "year")
        );
        scope.sectionTier = upperValue(
                first(safeArgs, "sectionTier", "section_tier"),
                first(query, "sectionTier", "section_tier"),
                first(extra, "sectionTier", "section_tier"),
                first(rawContext, "sectionTier", "section_tier")
        );
        scope.contextScope = upperValue(
                first(safeArgs, "contextScope", "context_scope"),
                mapContext == null ? null : mapContext.getMode(),
                first(rawContext, "contextScope", "context_scope")
        );
        scope.objectType = upperValue(
                first(safeArgs, "objectType", "object_type", "type", "layerType"),
                first(mapObject, "objectType", "object_type", "type", "layerType"),
                first(selected, "objectType", "object_type", "type", "layerType")
        );
        scope.objectId = stringValue(
                first(safeArgs, "objectId", "object_id", "id"),
                first(mapObject, "objectId", "object_id", "id"),
                first(selected, "objectId", "object_id", "id")
        );
        scope.assessmentObjectType = upperValue(
                first(safeArgs, "assessmentObjectType", "assessment_object_type"),
                first(rawObject, "objectType", "object_type"),
                first(selected, "assessmentObjectType", "assessment_object_type")
        );
        scope.direction = upperValue(
                first(safeArgs, "direction"),
                first(mapObject, "direction"),
                first(selected, "direction"),
                first(rawObject, "direction")
        );
        scope.startStake = doubleValue(
                first(safeArgs, "stakeStart", "startStake", "start_stake"),
                first(mapObject, "stakeStart", "startStake", "start_stake"),
                first(selected, "stakeStart", "startStake", "start_stake"),
                first(rawObject, "stakeStart", "startStake", "start_stake")
        );
        scope.endStake = doubleValue(
                first(safeArgs, "stakeEnd", "endStake", "end_stake"),
                first(mapObject, "stakeEnd", "endStake", "end_stake"),
                first(selected, "stakeEnd", "endStake", "end_stake"),
                first(rawObject, "stakeEnd", "endStake", "end_stake")
        );
        scope.bbox = first(safeArgs, "bbox");
        if (scope.bbox == null) {
            scope.bbox = first(viewport, "bbox");
        }
        scope.geometryType = upperValue(
                first(safeArgs, "geometryType", "geometry_type"),
                first(geometry, "type"),
                first(extra, "regionGeometryType", "region_geometry_type")
        );
        scope.selectedLayers = stringList(
                mapContext == null ? null : mapContext.getSelectedLayers(),
                first(rawContext, "selectedLayers", "selected_layers"),
                first(safeArgs, "selectedLayers", "selected_layers")
        );
        scope.scopeWarnings = scope.buildWarnings(rawContext);
        return scope;
    }

    public Map<String, Object> toQueryScope() {
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "tenantId", tenantId);
        put(data, "projectId", projectId);
        put(data, "routeCode", routeCode);
        put(data, "year", year);
        put(data, "sectionTier", sectionTier);
        put(data, "contextScope", contextScope);
        put(data, "objectType", objectType);
        put(data, "objectId", objectId);
        put(data, "assessmentObjectType", assessmentObjectType);
        put(data, "direction", direction);
        put(data, "startStake", startStake);
        put(data, "endStake", endStake);
        put(data, "bbox", bbox);
        put(data, "geometryType", geometryType);
        if (selectedLayers != null && !selectedLayers.isEmpty()) {
            data.put("selectedLayers", new ArrayList<>(selectedLayers));
        }
        if (scopeWarnings != null && !scopeWarnings.isEmpty()) {
            data.put("scopeWarnings", new ArrayList<>(scopeWarnings));
        }
        return data;
    }

    public List<String> assessmentObjectTypesForTier() {
        if ("LINE".equals(sectionTier)) {
            return Collections.singletonList("ROAD_SECTION_LINE");
        }
        if ("LEDGER".equals(sectionTier)) {
            List<String> values = new ArrayList<>();
            values.add("ROAD_SECTION_LEDGER");
            values.add("EVALUATION_UNIT");
            return values;
        }
        if ("KM".equals(sectionTier)) {
            return Collections.singletonList("ROAD_SECTION_KM");
        }
        if ("HM".equals(sectionTier)) {
            return Collections.singletonList("ROAD_SECTION_HM");
        }
        return Collections.emptyList();
    }

    private List<String> buildWarnings(Map<String, Object> rawContext) {
        List<String> warnings = new ArrayList<>();
        boolean looksProjectBound = notBlank(routeCode) || (selectedLayers != null && !selectedLayers.isEmpty()) || (rawContext != null && !rawContext.isEmpty());
        if (looksProjectBound && !notBlank(projectId)) {
            warnings.add("PROJECT_ID_MISSING");
        }
        if (notBlank(sectionTier) && assessmentObjectTypesForTier().isEmpty()) {
            warnings.add("SECTION_TIER_UNKNOWN");
        }
        if (startStake != null && endStake != null && startStake.doubleValue() > endStake.doubleValue()) {
            warnings.add("STAKE_RANGE_REVERSED");
        }
        return warnings;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Object first(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) return value;
        }
        Map<String, Object> raw = asMap(data.get("raw"));
        if (!raw.isEmpty()) {
            return first(raw, keys);
        }
        return null;
    }

    private static String stringValue(Object... values) {
        for (Object value : values) {
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String upperValue(Object... values) {
        String value = stringValue(values);
        return value.length() == 0 ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static Integer integerValue(Object... values) {
        String value = stringValue(values);
        if (value.length() == 0) return null;
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double doubleValue(Object... values) {
        String value = stringValue(values);
        if (value.length() == 0) return null;
        try {
            return Double.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> stringList(Object... values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) {
                    String text = stringValue(item);
                    if (text.length() > 0) result.add(text);
                }
                if (!result.isEmpty()) return result;
            }
        }
        return result;
    }

    private static void put(Map<String, Object> data, String key, Object value) {
        if (value == null) return;
        if (value instanceof String && ((String) value).trim().length() == 0) return;
        data.put(key, value);
    }

    private static boolean notBlank(String value) {
        return value != null && value.trim().length() > 0;
    }

    public String getTenantId() { return tenantId; }
    public String getProjectId() { return projectId; }
    public String getRouteCode() { return routeCode; }
    public Integer getYear() { return year; }
    public String getSectionTier() { return sectionTier; }
    public String getContextScope() { return contextScope; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getAssessmentObjectType() { return assessmentObjectType; }
    public String getDirection() { return direction; }
    public Double getStartStake() { return startStake; }
    public Double getEndStake() { return endStake; }
    public Object getBbox() { return bbox; }
    public String getGeometryType() { return geometryType; }
    public List<String> getSelectedLayers() { return selectedLayers; }
    public List<String> getScopeWarnings() { return scopeWarnings; }
}
