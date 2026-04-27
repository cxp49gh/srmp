package com.smartroad.srmp.agent.map;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class MapObjectContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean present;
    private String objectId;
    private String objectType;
    private String routeCode;
    private Integer year;
    private Map<String, Object> detail;
    private String markdown;

    public static MapObjectContext empty() {
        MapObjectContext ctx = new MapObjectContext();
        ctx.setPresent(false);
        return ctx;
    }

    public static MapObjectContext of(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        MapObjectContext ctx = new MapObjectContext();
        ctx.setPresent(true);
        ctx.setObjectId(String.valueOf(map.get("objectId")));
        ctx.setObjectType(String.valueOf(map.get("objectType")));
        ctx.setRouteCode(String.valueOf(map.get("routeCode")));
        Object year = map.get("year");
        if (year instanceof Number) {
            ctx.setYear(((Number) year).intValue());
        }
        ctx.setDetail(map);
        return ctx;
    }

    public String getMarkdown() {
        if (markdown != null) {
            return markdown;
        }
        if (!present || detail == null || detail.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【当前地图选中对象】\n");
        append(sb, "对象类型", objectType);
        append(sb, "对象ID", objectId);
        append(sb, "路线", routeCode);
        append(sb, "年度", year == null ? null : String.valueOf(year));
        return sb.toString();
    }

    private void append(StringBuilder sb, String label, Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return;
        sb.append("- ").append(label).append("：").append(value).append("\n");
    }
}
