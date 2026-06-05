package com.smartroad.srmp.agent.map;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapObjectContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean present;
    private String objectId;
    private String objectType;
    private String routeCode;
    private Integer year;
    private Map detail;
    private String markdown;

    public static MapObjectContext empty() {
        MapObjectContext ctx = new MapObjectContext();
        ctx.setPresent(false);
        ctx.setDetail(new LinkedHashMap());
        ctx.setMarkdown("");
        return ctx;
    }

    public static MapObjectContext of(Map map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        MapObjectContext ctx = new MapObjectContext();
        ctx.setPresent(true);
        ctx.setDetail(map);
        ctx.setObjectId(firstString(map, "objectId", "object_id", "id"));
        ctx.setObjectType(normalizeType(firstString(map, "objectType", "object_type", "type", "layerType")));
        ctx.setRouteCode(firstString(map, "routeCode", "route_code"));
        ctx.setYear(firstInteger(map, "year"));
        ctx.setMarkdown(buildMarkdown(map, ctx.getObjectType()));
        return ctx;
    }

    public String getMarkdown() {
        if (markdown != null) {
            return markdown;
        }
        if (!present || detail == null || detail.isEmpty()) {
            return "";
        }
        markdown = buildMarkdown(detail, objectType);
        return markdown;
    }

    private static String buildMarkdown(Map map, String objectType) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前地图选中对象】\n");
        append(sb, "对象类型", objectType);
        append(sb, "对象ID", firstString(map, "objectId", "object_id", "id"));
        append(sb, "路线", firstString(map, "routeCode", "route_code"));
        append(sb, "名称", firstString(map, "routeName", "route_name", "sectionName", "section_name", "unitCode", "unit_code"));
        append(sb, "起点桩号", firstObject(map, "startStake", "start_stake"));
        append(sb, "终点桩号", firstObject(map, "endStake", "end_stake"));
        append(sb, "MQI", firstObject(map, "mqi"));
        append(sb, "PQI", firstObject(map, "pqi"));
        append(sb, "PCI", firstObject(map, "pci"));
        append(sb, "等级", firstObject(map, "grade"));
        append(sb, "病害", firstString(map, "diseaseName", "disease_name", "diseaseType", "disease_type"));
        append(sb, "严重程度", firstObject(map, "severity"));
        append(sb, "数量", firstObject(map, "quantity"));
        append(sb, "单位", firstObject(map, "measureUnit", "measure_unit"));
        append(sb, "摘要", firstString(map, "context_summary", "contextSummary"));
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return;
        }
        sb.append("- ").append(label).append("：").append(value).append("\n");
    }

    private static String normalizeType(String value) {
        if (value == null) {
            return null;
        }
        String type = value.trim().toUpperCase().replace("-", "_");
        if ("ASSESSMENT".equals(type)) {
            return "ASSESSMENT_RESULT";
        }
        if ("DISEASE_RECORD".equals(type)) {
            return "DISEASE";
        }
        return type;
    }

    private static String firstString(Map map, String... keys) {
        Object value = firstObject(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer firstInteger(Map map, String... keys) {
        Object value = firstObject(map, keys);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Object firstObject(Map map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }
}
