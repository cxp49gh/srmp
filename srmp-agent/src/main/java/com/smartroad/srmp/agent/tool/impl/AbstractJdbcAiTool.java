package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import com.smartroad.srmp.agent.tool.support.AiBusinessScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.annotation.Resource;
import java.sql.Types;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractJdbcAiTool implements AiTool {
    @Resource
    protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public boolean supports(AiToolContext context) {
        return true;
    }

    protected MapSqlParameterSource baseParams(AiToolContext context) {
        return baseParams(context, null);
    }

    protected MapSqlParameterSource baseParams(AiToolContext context, Map<String, Object> args) {
        MapAiContext map = context == null ? null : context.getMapContext();
        AiBusinessScope scope = businessScope(context, args);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("tenantId", safe(scope.getTenantId()).length() == 0 ? (context == null ? null : context.getTenantId()) : scope.getTenantId());
        p.addValue("projectId", safe(scope.getProjectId()));
        p.addValue("routeCode", safe(scope.getRouteCode()).length() == 0 ? safe(map == null ? null : map.getRouteCode()) : safe(scope.getRouteCode()));
        p.addValue("year", scope.getYear() == null ? (map == null || map.getYear() == null ? "" : String.valueOf(map.getYear())) : String.valueOf(scope.getYear()));
        p.addValue("sectionTier", safe(scope.getSectionTier()));
        p.addValue("assessmentObjectTypes", scope.assessmentObjectTypesForTier().isEmpty() ? Collections.singletonList("__NONE__") : scope.assessmentObjectTypesForTier());
        p.addValue("contextScope", safe(scope.getContextScope()));
        p.addValue("objectType", safe(scope.getObjectType()));
        p.addValue("objectId", safe(scope.getObjectId()));
        p.addValue("assessmentObjectType", safe(scope.getAssessmentObjectType()));
        p.addValue("direction", safe(scope.getDirection()));
        p.addValue("startStake", scope.getStartStake(), Types.NUMERIC);
        p.addValue("endStake", scope.getEndStake(), Types.NUMERIC);
        return p;
    }

    protected AiBusinessScope businessScope(AiToolContext context, Map<String, Object> args) {
        return AiBusinessScope.from(context, args);
    }

    protected String routeFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:routeCode,'') is null or " + prefix + "route_code = nullif(:routeCode,'')) ";
    }

    protected String yearFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:year,'') is null or cast(" + prefix + "year as text)=nullif(:year,'')) ";
    }

    protected String directProjectFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:projectId,'') is null or " + prefix + "project_id = nullif(:projectId,'')) ";
    }

    protected String assessmentProjectFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:projectId,'') is null or exists (select 1 from road_route rr where rr.tenant_id=" + prefix + "tenant_id and rr.route_code=" + prefix + "route_code and rr.project_id=nullif(:projectId,'') and rr.deleted=false)) ";
    }

    protected String sectionTierFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:sectionTier,'') is null or " + prefix + "object_type in (:assessmentObjectTypes)) ";
    }

    protected String directionFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:direction,'') is null or " + prefix + "direction is null or " + prefix + "direction = nullif(:direction,'') or upper(" + prefix + "direction)='BOTH') ";
    }

    protected String stakeOverlapFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (:startStake is null or :endStake is null or (coalesce(" + prefix + "start_stake," + prefix + "end_stake) <= :endStake and coalesce(" + prefix + "end_stake," + prefix + "start_stake) >= :startStake)) ";
    }

    protected String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    protected AiToolResult failedResult(String message, long start) {
        return AiToolResult.failed(name(), message, System.currentTimeMillis() - start);
    }

    protected Map<String, Object> map(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }
}
