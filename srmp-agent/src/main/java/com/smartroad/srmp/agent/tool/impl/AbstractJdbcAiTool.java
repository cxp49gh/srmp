package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.annotation.Resource;
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
        MapAiContext map = context == null ? null : context.getMapContext();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("tenantId", context == null ? null : context.getTenantId());
        p.addValue("routeCode", safe(map == null ? null : map.getRouteCode()));
        p.addValue("year", map == null || map.getYear() == null ? "" : String.valueOf(map.getYear()));
        return p;
    }

    protected String routeFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:routeCode,'') is null or " + prefix + "route_code = nullif(:routeCode,'')) ";
    }

    protected String yearFilter(String alias) {
        String prefix = alias == null || alias.length() == 0 ? "" : alias + ".";
        return " and (nullif(:year,'') is null or cast(" + prefix + "year as text)=nullif(:year,'')) ";
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
