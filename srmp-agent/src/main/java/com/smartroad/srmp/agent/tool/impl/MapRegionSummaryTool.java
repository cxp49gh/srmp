package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import com.smartroad.srmp.agent.tool.support.AiBusinessScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapRegionSummaryTool extends AbstractJdbcAiTool {
    @Override
    public String name() { return "gis.queryRegionSummary"; }
    @Override
    public String description() { return "查询当前路线或框选区域的路况统计摘要"; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            AiBusinessScope scope = businessScope(context, args);
            MapAiContext map = context == null ? null : context.getMapContext();
            if (map != null && map.getRegionSummary() != null && !map.getRegionSummary().isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>(map.getRegionSummary());
                data.put("queryScope", scope.toQueryScope());
                data.put("scopeWarnings", scope.getScopeWarnings());
                data.put("returnedCount", 1);
                data.put("totalCount", 1);
                data.put("truncated", false);
                return AiToolResult.success(name(), "使用前端已传入的区域统计摘要", data, 1, System.currentTimeMillis() - start);
            }
            MapSqlParameterSource p = baseParams(context, args);
            String diseaseFilters = routeFilter("d") + directProjectFilter("d") + directionFilter("d") + stakeOverlapFilter("d");
            String assessmentFilters = routeFilter("a") + yearFilter("a") + assessmentProjectFilter("a") + sectionTierFilter("a") + directionFilter("a") + stakeOverlapFilter("a");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("diseaseCount", first("select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + diseaseFilters, p));
            data.put("assessmentCount", first("select count(*) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters, p));
            data.put("avgMqi", first("select round(avg(a.mqi),3) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters, p));
            data.put("heavyDiseaseCount", first("select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false and d.severity='HEAVY' " + diseaseFilters, p));
            List<Map<String, Object>> diseaseByType = namedParameterJdbcTemplate.queryForList(
                    "select d.disease_name, d.severity, count(*) count from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + diseaseFilters +
                            " group by d.disease_name, d.severity order by count desc limit 10", p);
            data.put("diseaseByType", diseaseByType);
            data.put("queryScope", scope.toQueryScope());
            data.put("scopeWarnings", scope.getScopeWarnings());
            data.put("returnedCount", diseaseByType.size());
            data.put("totalCount", data.get("diseaseCount"));
            data.put("truncated", false);
            return AiToolResult.success(name(), "已生成区域/路线统计摘要", data, diseaseByType.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private Object first(String sql, MapSqlParameterSource p) {
        return namedParameterJdbcTemplate.queryForObject(sql, p, Object.class);
    }
}
