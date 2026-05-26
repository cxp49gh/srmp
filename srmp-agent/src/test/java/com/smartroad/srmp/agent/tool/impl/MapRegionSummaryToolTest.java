package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.junit.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Types;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapRegionSummaryToolTest {

    @Test
    public void queryRegionSummaryUsesGeometrySqlWhenGeometryIsPresent() throws Exception {
        MapRegionSummaryTool tool = new MapRegionSummaryTool();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(tool, "namedParameterJdbcTemplate", jdbcTemplate);

        Map<String, Object> geometry = mapOf(
                "type", "Polygon",
                "coordinates", java.util.Collections.singletonList(java.util.Arrays.asList(
                        java.util.Arrays.asList(112.0, 37.0),
                        java.util.Arrays.asList(113.0, 37.0),
                        java.util.Arrays.asList(113.0, 38.0),
                        java.util.Arrays.asList(112.0, 37.0)
                ))
        );
        MapAiContext mapContext = new MapAiContext();
        mapContext.setTenantId("tenant-a");
        mapContext.setMode("REGION");
        mapContext.setRouteCode("G210");
        mapContext.setYear(2026);
        mapContext.setGeometry(geometry);
        mapContext.setRegionSummary(mapOf("routeCount", 99));

        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");
        context.setMapContext(mapContext);

        AiToolResult result = tool.execute(context, mapOf("limit", 50));

        assertTrue(result.isSuccess());
        assertEquals("POSTGIS", ((Map) result.getData()).get("sourcePrecision"));
        assertTrue(jdbcTemplate.joinedSql().contains("ST_GeomFromGeoJSON(:geometryGeoJson)"));
        assertTrue(jdbcTemplate.joinedSql().contains("ST_Intersects"));
        assertTrue(jdbcTemplate.lastParams.hasValue("geometryGeoJson"));
        MapSqlParameterSource params = (MapSqlParameterSource) jdbcTemplate.lastParams;
        assertEquals(Types.NUMERIC, params.getSqlType("startStake"));
        assertEquals(Types.NUMERIC, params.getSqlType("endStake"));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = AbstractJdbcAiTool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static class CapturingJdbcTemplate extends NamedParameterJdbcTemplate {
        private final List<String> sqlList = new ArrayList<>();
        private SqlParameterSource lastParams;

        CapturingJdbcTemplate() {
            super(new DriverManagerDataSource());
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource paramSource, Class<T> requiredType) {
            sqlList.add(sql);
            lastParams = paramSource;
            Object value = 1;
            return requiredType.cast(value);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, SqlParameterSource paramSource) {
            sqlList.add(sql);
            lastParams = paramSource;
            return new ArrayList<Map<String, Object>>();
        }

        String joinedSql() {
            StringBuilder sb = new StringBuilder();
            for (String sql : sqlList) {
                sb.append(sql).append('\n');
            }
            return sb.toString();
        }
    }
}
