package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
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
            MapSqlParameterSource p = baseParams(context);
            p.addValue("limit", limit(args));
            p.addValue("severity", args == null ? "" : safe(args.get("severity")));
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select id, route_code, start_stake, end_stake, disease_name, disease_type, severity, quantity, measure_unit " +
                            "from disease_record where tenant_id=:tenantId and deleted=false " + routeFilter("") +
                            " and (nullif(:severity,'') is null or severity=nullif(:severity,'')) " +
                            "order by case severity when 'HEAVY' then 1 when 'MEDIUM' then 2 else 3 end, start_stake nulls last limit :limit",
                    p);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", rows);
            data.put("count", rows.size());
            return AiToolResult.success(name(), "查询到 " + rows.size() + " 条病害记录", data, rows.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private int limit(Map<String, Object> args) {
        Object value = args == null ? null : args.get("limit");
        try {
            int limit = value == null ? 10 : Integer.parseInt(String.valueOf(value));
            return Math.max(1, Math.min(limit, 50));
        } catch (Exception e) {
            return 10;
        }
    }
}
