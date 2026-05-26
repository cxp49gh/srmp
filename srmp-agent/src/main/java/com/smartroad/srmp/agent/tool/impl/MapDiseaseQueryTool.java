package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import com.smartroad.srmp.agent.tool.support.AiBusinessScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapDiseaseQueryTool extends AbstractJdbcAiTool {
    @Override
    public String name() { return "gis.queryDiseases"; }
    @Override
    public String description() { return "查询当前路线、对象或区域相关病害"; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            AiBusinessScope scope = businessScope(context, args);
            MapSqlParameterSource p = baseParams(context, args);
            p.addValue("limit", limit(args));
            p.addValue("severity", args == null ? "" : safe(args.get("severity")));
            p.addValue("diseaseType", args == null ? "" : safe(args.get("diseaseType")));
            String filters = routeFilter("d") + directProjectFilter("d") + directionFilter("d") + stakeOverlapFilter("d") +
                    " and (nullif(:severity,'') is null or d.severity=nullif(:severity,'')) " +
                    " and (nullif(:diseaseType,'') is null or d.disease_type=nullif(:diseaseType,'')) ";
            Integer total = namedParameterJdbcTemplate.queryForObject(
                    "select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + filters, p, Integer.class);
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select d.id, d.route_code, d.direction, d.start_stake, d.end_stake, d.disease_name, d.disease_type, d.severity, d.quantity, d.measure_unit " +
                            "from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + filters +
                            "order by case d.severity when 'HEAVY' then 1 when 'MEDIUM' then 2 else 3 end, d.start_stake nulls last limit :limit",
                    p);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", rows);
            data.put("count", rows.size());
            data.put("returnedCount", rows.size());
            data.put("totalCount", total == null ? rows.size() : total);
            data.put("truncated", total != null && total > rows.size());
            data.put("queryScope", scope.toQueryScope());
            data.put("scopeWarnings", scope.getScopeWarnings());
            data.put("summary", summarize(rows));
            return AiToolResult.success(name(), "查询到 " + rows.size() + " 条病害记录（总数 " + (total == null ? rows.size() : total) + "）", data, rows.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private Map<String, Object> summarize(List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Integer> diseaseTypes = new LinkedHashMap<>();
        Map<String, Integer> severities = new LinkedHashMap<>();
        double totalQuantity = 0.0d;

        for (Map<String, Object> row : rows) {
            String name = safe(first(row, "disease_name", "diseaseName", "disease_type", "diseaseType"));
            String severity = safe(first(row, "severity"));
            if (name.length() == 0) name = "UNKNOWN";
            if (severity.length() == 0) severity = "UNKNOWN";
            diseaseTypes.put(name, diseaseTypes.getOrDefault(name, 0) + 1);
            severities.put(severity, severities.getOrDefault(severity, 0) + 1);
            try {
                Object quantity = first(row, "quantity");
                if (quantity != null) totalQuantity += Double.parseDouble(String.valueOf(quantity));
            } catch (Exception ignored) {
            }
        }

        summary.put("diseaseTypes", diseaseTypes);
        summary.put("severities", severities);
        summary.put("totalQuantity", totalQuantity);
        return summary;
    }

    private Object first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private int limit(Map<String, Object> args) {
        Object value = args == null ? null : args.get("limit");
        try {
            int limit = value == null ? 50 : Integer.parseInt(String.valueOf(value));
            return Math.max(1, Math.min(limit, 200));
        } catch (Exception e) {
            return 50;
        }
    }
}
