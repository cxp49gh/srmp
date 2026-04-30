package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
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
            MapAiContext map = context == null ? null : context.getMapContext();
            if (map != null && map.getRegionSummary() != null && !map.getRegionSummary().isEmpty()) {
                return AiToolResult.success(name(), "使用前端已传入的区域统计摘要", map.getRegionSummary(), 1, System.currentTimeMillis() - start);
            }
            MapSqlParameterSource p = baseParams(context);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("diseaseCount", first("select count(*) from disease_record where tenant_id=:tenantId and deleted=false " + routeFilter(""), p));
            data.put("assessmentCount", first("select count(*) from assessment_result where tenant_id=:tenantId and deleted=false " + routeFilter("") + yearFilter(""), p));
            data.put("avgMqi", first("select round(avg(mqi),3) from assessment_result where tenant_id=:tenantId and deleted=false " + routeFilter("") + yearFilter(""), p));
            data.put("heavyDiseaseCount", first("select count(*) from disease_record where tenant_id=:tenantId and deleted=false and severity='HEAVY' " + routeFilter(""), p));
            List<Map<String, Object>> diseaseByType = namedParameterJdbcTemplate.queryForList(
                    "select disease_name, severity, count(*) count from disease_record where tenant_id=:tenantId and deleted=false " + routeFilter("") +
                            " group by disease_name, severity order by count desc limit 10", p);
            data.put("diseaseByType", diseaseByType);
            return AiToolResult.success(name(), "已生成区域/路线统计摘要", data, diseaseByType.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private Object first(String sql, MapSqlParameterSource p) {
        return namedParameterJdbcTemplate.queryForObject(sql, p, Object.class);
    }
}
