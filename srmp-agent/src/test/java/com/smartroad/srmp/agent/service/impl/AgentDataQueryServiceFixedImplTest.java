package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.junit.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgentDataQueryServiceFixedImplTest {

    @Test
    public void routeSummaryUsesSectionLengthForRouteLength() throws Exception {
        AgentDataQueryServiceFixedImpl service = new AgentDataQueryServiceFixedImpl();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbcTemplate);
        TenantContextHolder.setTenantId("tenant-a");

        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setRouteCode("Y016140727");

        try {
            service.routeSummary(request);
        } finally {
            TenantContextHolder.clear();
        }

        String sql = jdbcTemplate.sqlAt(0);
        assertTrue(sql.contains("road_section_line"));
        assertFalse(sql.contains("sum(distinct r.length_km)"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = AgentDataQueryServiceFixedImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class CapturingJdbcTemplate extends NamedParameterJdbcTemplate {
        private final List<String> sqlList = new ArrayList<>();

        CapturingJdbcTemplate() {
            super(new DriverManagerDataSource());
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, SqlParameterSource paramSource) {
            sqlList.add(sql);
            return new ArrayList<Map<String, Object>>();
        }

        String sqlAt(int index) {
            return sqlList.get(index);
        }
    }
}
