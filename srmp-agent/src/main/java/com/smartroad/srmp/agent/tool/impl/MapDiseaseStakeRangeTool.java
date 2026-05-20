package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import com.smartroad.srmp.agent.tool.support.AiBusinessScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase37.4.4：按当前评定单元桩号范围查询病害。
 *
 * 用于 ASSESSMENT_RESULT 对象：
 * - 输入 startStake / endStake；
 * - 查询 disease_record 中落入或重叠该区间的病害；
 * - 让评定结果回答从“建议排查病害”升级为“结合单元内病害明细判断”。
 */
@Component
public class MapDiseaseStakeRangeTool extends AbstractJdbcAiTool {

    @Override
    public String name() {
        return "gis.queryDiseasesByStakeRange";
    }

    @Override
    public String description() {
        return "按当前评定单元桩号范围查询单元内病害记录";
    }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long startTime = System.currentTimeMillis();
        try {
            AiBusinessScope scope = businessScope(context, args);
            MapAiContext map = context == null ? null : context.getMapContext();
            Map<String, Object> obj = map == null ? null : map.getMapObject();

            BigDecimal startStake = scope.getStartStake() == null ? toDecimal(first(obj, "startStake", "start_stake", "startMileage", "start_mileage")) : toDecimal(scope.getStartStake());
            BigDecimal endStake = scope.getEndStake() == null ? toDecimal(first(obj, "endStake", "end_stake", "endMileage", "end_mileage")) : toDecimal(scope.getEndStake());
            if (startStake == null && endStake == null) {
                return AiToolResult.success(name(), "当前评定对象缺少起止桩号，无法查询单元内病害", new LinkedHashMap<String, Object>(), 0, System.currentTimeMillis() - startTime);
            }
            if (startStake == null) startStake = endStake;
            if (endStake == null) endStake = startStake;
            if (startStake.compareTo(endStake) > 0) {
                BigDecimal tmp = startStake;
                startStake = endStake;
                endStake = tmp;
            }

            MapSqlParameterSource p = baseParams(context, args);
            p.addValue("startStake", startStake);
            p.addValue("endStake", endStake);
            p.addValue("limit", limit(args));

            String filters = routeFilter("d") + directProjectFilter("d") + directionFilter("d") + stakeOverlapFilter("d");
            Integer total = namedParameterJdbcTemplate.queryForObject(
                    "select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + filters, p, Integer.class);
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select d.id, d.route_code, d.direction, d.start_stake, d.end_stake, d.disease_name, d.disease_type, d.severity, d.quantity, d.measure_unit " +
                            "from disease_record d " +
                            "where d.tenant_id=:tenantId and d.deleted=false " + filters +
                            "order by case d.severity when 'HEAVY' then 1 when 'MEDIUM' then 2 else 3 end, d.start_stake nulls last " +
                            "limit :limit",
                    p
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", rows);
            data.put("count", rows.size());
            data.put("returnedCount", rows.size());
            data.put("totalCount", total == null ? rows.size() : total);
            data.put("truncated", total != null && total > rows.size());
            data.put("startStake", startStake);
            data.put("endStake", endStake);
            data.put("queryScope", scope.toQueryScope());
            data.put("scopeWarnings", scope.getScopeWarnings());
            data.put("summary", summarize(rows));

            return AiToolResult.success(name(), "查询到评定单元内病害 " + rows.size() + " 条", data, rows.size(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return failedResult(e.getMessage(), startTime);
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
            diseaseTypes.put(name.length() == 0 ? "UNKNOWN" : name, diseaseTypes.getOrDefault(name.length() == 0 ? "UNKNOWN" : name, 0) + 1);
            severities.put(severity.length() == 0 ? "UNKNOWN" : severity, severities.getOrDefault(severity.length() == 0 ? "UNKNOWN" : severity, 0) + 1);
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

    private Object first(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) return value;
        }
        Object raw = map.get("raw");
        if (raw instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) raw;
            for (String key : keys) {
                Object value = rawMap.get(key);
                if (value != null && String.valueOf(value).trim().length() > 0) return value;
            }
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return null; }
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
