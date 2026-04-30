package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
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
            MapSqlParameterSource p = baseParams(context);
            p.addValue("limit", 20);
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select id, route_code, start_stake, end_stake, year, mqi, pqi, pci, rqi, rdi, grade " +
                            "from assessment_result where tenant_id=:tenantId and deleted=false " + routeFilter("") + yearFilter("") +
                            "order by mqi nulls last limit :limit", p);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", rows);
            data.put("count", rows.size());
            return AiToolResult.success(name(), "查询到 " + rows.size() + " 条评定结果", data, rows.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }
}
