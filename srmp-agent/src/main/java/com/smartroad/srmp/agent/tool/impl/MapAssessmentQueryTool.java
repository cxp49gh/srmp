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
public class MapAssessmentQueryTool extends AbstractJdbcAiTool {
    @Override
    public String name() { return "gis.queryAssessmentResults"; }
    @Override
    public String description() { return "查询路线或区域相关评定结果"; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            AiBusinessScope scope = businessScope(context, args);
            MapSqlParameterSource p = baseParams(context, args);
            p.addValue("limit", limit(args));
            p.addValue("grade", args == null ? "" : safe(args.get("grade")));
            String filters = routeFilter("a") + yearFilter("a") + assessmentProjectFilter("a") + sectionTierFilter("a") + directionFilter("a") + stakeOverlapFilter("a") +
                    " and (nullif(:grade,'') is null or a.grade=nullif(:grade,'')) " +
                    " and (nullif(:objectId,'') is null or upper(nullif(:objectType,'')) <> 'ASSESSMENT_RESULT' or a.id=nullif(:objectId,'') or a.object_id=nullif(:objectId,'')) ";
            Integer total = namedParameterJdbcTemplate.queryForObject(
                    "select count(*) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + filters, p, Integer.class);
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select a.id, a.object_type, a.object_id, a.route_code, a.direction, a.start_stake, a.end_stake, a.year, a.mqi, a.pqi, a.pci, a.rqi, a.rdi, a.grade " +
                            "from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + filters +
                            "order by a.mqi nulls last limit :limit", p);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", rows);
            data.put("count", rows.size());
            data.put("returnedCount", rows.size());
            data.put("totalCount", total == null ? rows.size() : total);
            data.put("truncated", total != null && total > rows.size());
            data.put("queryScope", scope.toQueryScope());
            data.put("scopeWarnings", scope.getScopeWarnings());
            return AiToolResult.success(name(), "查询到 " + rows.size() + " 条评定结果（总数 " + (total == null ? rows.size() : total) + "）", data, rows.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private int limit(Map<String, Object> args) {
        Object value = args == null ? null : args.get("limit");
        try {
            int limit = value == null ? 20 : Integer.parseInt(String.valueOf(value));
            return Math.max(1, Math.min(limit, 200));
        } catch (Exception e) {
            return 20;
        }
    }
}
