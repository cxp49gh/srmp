package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapNearbyObjectTool extends AbstractJdbcAiTool {
    @Override
    public String name() { return "gis.queryNearbyObjects"; }
    @Override
    public String description() { return "查询当前地图对象周边病害和评定结果"; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            MapAiContext map = context == null ? null : context.getMapContext();
            Map<String, Object> obj = map == null ? null : map.getMapObject();
            BigDecimal stake = toDecimal(first(obj, "startStake", "start_stake"));
            if (stake == null) {
                return AiToolResult.success(name(), "当前对象缺少桩号，无法按桩号查询周边对象", new LinkedHashMap<String, Object>(), 0, System.currentTimeMillis() - start);
            }
            BigDecimal delta = new BigDecimal("1.0");
            MapSqlParameterSource p = baseParams(context);
            p.addValue("start", stake.subtract(delta));
            p.addValue("end", stake.add(delta));
            List<Map<String, Object>> diseases = namedParameterJdbcTemplate.queryForList(
                    "select id, route_code, start_stake, end_stake, disease_name, severity, quantity, measure_unit from disease_record " +
                            "where tenant_id=:tenantId and deleted=false " + routeFilter("") +
                            " and start_stake between :start and :end order by start_stake limit 20", p);
            List<Map<String, Object>> assessments = namedParameterJdbcTemplate.queryForList(
                    "select id, route_code, start_stake, end_stake, year, mqi, pqi, pci, grade from assessment_result " +
                            "where tenant_id=:tenantId and deleted=false " + routeFilter("") + yearFilter("") +
                            " and start_stake between :start and :end order by start_stake limit 20", p);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("diseases", diseases);
            data.put("assessments", assessments);
            int count = diseases.size() + assessments.size();
            return AiToolResult.success(name(), "查询到周边对象 " + count + " 条", data, count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private Object first(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) return value;
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return null; }
    }
}
