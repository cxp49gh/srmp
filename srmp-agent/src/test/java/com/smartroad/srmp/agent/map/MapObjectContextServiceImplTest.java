package com.smartroad.srmp.agent.map;

import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.junit.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class MapObjectContextServiceImplTest {

    @Test
    public void routeObjectContextUsesSectionLengthInContextSummary() throws Exception {
        MapObjectContextServiceImpl service = new MapObjectContextServiceImpl();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbcTemplate);
        TenantContextHolder.setTenantId("tenant-a");

        try {
            service.getObjectDetail("ROAD_ROUTE", "route-1", "Y016140727", 2026);
        } finally {
            TenantContextHolder.clear();
        }

        String sql = jdbcTemplate.sqlAt(0);
        assertTrue(sql.contains("road_section_line"));
        assertTrue(sql.contains("coalesce(sec.length_km"));
        assertTrue(sql.contains("context_summary"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = MapObjectContextServiceImpl.class.getDeclaredField(fieldName);
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
