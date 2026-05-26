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
            AiBusinessScope scope = businessScope(context, args);
            MapAiContext map = context == null ? null : context.getMapContext();
            Map<String, Object> obj = map == null ? null : map.getMapObject();
            BigDecimal stake = toDecimal(first(obj, "startStake", "start_stake"));
            if (stake == null && scope.getStartStake() != null) {
                stake = toDecimal(scope.getStartStake());
            }
            if (stake == null) {
                return AiToolResult.success(name(), "当前对象缺少桩号，无法按桩号查询周边对象", new LinkedHashMap<String, Object>(), 0, System.currentTimeMillis() - start);
            }
            BigDecimal delta = new BigDecimal("1.0");
            MapSqlParameterSource p = baseParams(context, args);
            p.addValue("startStake", stake.subtract(delta));
            p.addValue("endStake", stake.add(delta));
            List<Map<String, Object>> diseases = namedParameterJdbcTemplate.queryForList(
                    "select d.id, d.route_code, d.direction, d.start_stake, d.end_stake, d.disease_name, d.severity, d.quantity, d.measure_unit from disease_record d " +
                            "where d.tenant_id=:tenantId and d.deleted=false " + routeFilter("d") + directProjectFilter("d") + directionFilter("d") + stakeOverlapFilter("d") +
                            " order by d.start_stake limit 20", p);
            List<Map<String, Object>> assessments = namedParameterJdbcTemplate.queryForList(
                    "select a.id, a.object_type, a.object_id, a.route_code, a.direction, a.start_stake, a.end_stake, a.year, a.mqi, a.pqi, a.pci, a.grade from assessment_result a " +
                            "where a.tenant_id=:tenantId and a.deleted=false " + routeFilter("a") + yearFilter("a") + assessmentProjectFilter("a") + sectionTierFilter("a") + directionFilter("a") + stakeOverlapFilter("a") +
                            " order by a.start_stake limit 20", p);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("diseases", diseases);
            data.put("assessments", assessments);
            data.put("queryScope", scope.toQueryScope());
            data.put("scopeWarnings", scope.getScopeWarnings());
            data.put("searchStartStake", stake.subtract(delta));
            data.put("searchEndStake", stake.add(delta));
            int count = diseases.size() + assessments.size();
            data.put("count", count);
            data.put("returnedCount", count);
            data.put("totalCount", count);
            data.put("truncated", false);
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
