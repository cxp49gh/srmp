package com.smartroad.srmp.agent.trace.service.impl;

import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.AiTraceStep;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;

@Service
public class AiTraceServiceImpl implements AiTraceService {
    @Resource private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Override public void save(AiTraceContext trace) {
        if (trace == null) return;
        String tenantId = TenantContextHolder.getTenantId();
        namedParameterJdbcTemplate.update("insert into ai_trace_log(id,tenant_id,trace_id,request_type,user_message,mode,status,total_cost_ms,fallback,error_message,created_at) values(:id,:tenantId,:traceId,:requestType,:userMessage,:mode,:status,:totalCostMs,:fallback,:errorMessage,now())",
            new MapSqlParameterSource().addValue("id", uuid()).addValue("tenantId", tenantId).addValue("traceId", trace.getTraceId())
            .addValue("requestType", trace.getRequestType()).addValue("userMessage", trace.getMessage()).addValue("mode", trace.getMode())
            .addValue("status", trace.getStatus()).addValue("totalCostMs", trace.getTotalCostMs()==null?null:trace.getTotalCostMs().intValue())
            .addValue("fallback", Boolean.TRUE.equals(trace.getFallback())).addValue("errorMessage", trace.getError()));
        for (AiTraceStep step : trace.getSteps()) {
            namedParameterJdbcTemplate.update("insert into ai_trace_step(id,tenant_id,trace_id,step_name,step_label,status,cost_ms,hit_count,error_message,created_at) values(:id,:tenantId,:traceId,:stepName,:stepLabel,:status,:costMs,:hitCount,:errorMessage,now())",
                new MapSqlParameterSource().addValue("id", uuid()).addValue("tenantId", tenantId).addValue("traceId", trace.getTraceId())
                .addValue("stepName", step.getName()).addValue("stepLabel", step.getLabel()).addValue("status", step.getStatus())
                .addValue("costMs", step.getCostMs()==null?null:step.getCostMs().intValue()).addValue("hitCount", step.getCount()).addValue("errorMessage", step.getError()));
        }
    }
    @Override public List<Map<String,Object>> list(String status, String keyword, Integer limit) {
        int size = limit == null ? 50 : limit; if (size <= 0) size = 50; if (size > 200) size = 200;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("status", safe(status)).addValue("keyword", "%"+safe(keyword).toLowerCase(Locale.ROOT)+"%").addValue("limit", size);
        return namedParameterJdbcTemplate.queryForList("select id,tenant_id,trace_id,request_type,user_message,mode,status,total_cost_ms,fallback,error_message,created_at from ai_trace_log where tenant_id=:tenantId and (:status='' or status=:status) and (:keyword='%%' or lower(coalesce(user_message,'')) like :keyword or lower(coalesce(trace_id,'')) like :keyword) order by created_at desc limit :limit", p);
    }
    @Override public Map<String,Object> detail(String traceId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("traceId", traceId);
        List<Map<String,Object>> list = namedParameterJdbcTemplate.queryForList("select id,tenant_id,trace_id,request_type,user_message,mode,status,total_cost_ms,fallback,error_message,created_at from ai_trace_log where tenant_id=:tenantId and trace_id=:traceId order by created_at desc limit 1", p);
        if (list.isEmpty()) return new LinkedHashMap<>(); Map<String,Object> data = new LinkedHashMap<>(list.get(0)); data.put("steps", steps(traceId)); return data;
    }
    @Override public List<Map<String,Object>> steps(String traceId) {
        return namedParameterJdbcTemplate.queryForList("select id,tenant_id,trace_id,step_name,step_label,status,cost_ms,hit_count,error_message,created_at from ai_trace_step where tenant_id=:tenantId and trace_id=:traceId order by created_at asc", new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("traceId", traceId));
    }
    private String safe(String v){ return v==null?"":v.trim(); }
    private String uuid(){ return UUID.randomUUID().toString().replace("-",""); }
}
