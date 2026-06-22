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

    @Test
    public void queryRouteSummaryUsesSectionLengthForTotalLength() throws Exception {
        MapRegionSummaryTool tool = new MapRegionSummaryTool();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(tool, "namedParameterJdbcTemplate", jdbcTemplate);

        MapAiContext mapContext = new MapAiContext();
        mapContext.setTenantId("tenant-a");
        mapContext.setMode("OBJECT");
        mapContext.setRouteCode("Y016140727");
        mapContext.setYear(2026);
        mapContext.setMapObject(mapOf(
                "objectType", "ROAD_ROUTE",
                "routeCode", "Y016140727",
                "startStake", 0,
                "endStake", 14.072
        ));
        mapContext.setExtra(mapOf(
                "rawContext", mapOf("query", mapOf("projectId", "project-a", "sectionTier", "LINE"))
        ));

        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");
        context.setMapContext(mapContext);

        AiToolResult result = tool.execute(context, mapOf("limit", 50));

        assertTrue(result.isSuccess());
        String totalLengthSql =
                jdbcTemplate.firstSqlContaining("sum(coalesce(s.length_km");
        assertTrue(totalLengthSql.contains("from road_section_line s"));
        assertTrue(totalLengthSql.contains("sum(coalesce(s.length_km"));
    }

    @Test
    public void queryRouteSummaryAddsVerifiedRoadRouteTargetToQueryScope() throws Exception {
        MapRegionSummaryTool tool = new MapRegionSummaryTool();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        jdbcTemplate.enqueueList(java.util.Collections.singletonList(mapOf(
                "id", "route-1",
                "route_code", "C001140727",
                "start_stake", 0,
                "end_stake", 10.972
        )));
        setField(tool, "namedParameterJdbcTemplate", jdbcTemplate);

        MapAiContext mapContext = new MapAiContext();
        mapContext.setTenantId("tenant-a");
        mapContext.setMode("ROUTE");
        mapContext.setRouteCode("C001140727");
        mapContext.setExtra(mapOf(
                "rawContext", mapOf(
                        "query", mapOf("projectId", "project-a", "sectionTier", "LINE")
                )
        ));
        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");
        context.setMapContext(mapContext);

        AiToolResult result = tool.execute(context, mapOf("limit", 50));

        assertTrue(result.isSuccess());
        Map<?, ?> scope = (Map<?, ?>) ((Map<?, ?>) result.getData()).get("queryScope");
        assertEquals("ROAD_ROUTE", scope.get("objectType"));
        assertEquals("route-1", scope.get("objectId"));
        assertEquals("C001140727", scope.get("routeCode"));
        assertEquals(0, ((Number) scope.get("startStake")).intValue());
        assertEquals(10.972, ((Number) scope.get("endStake")).doubleValue(), 0.0001);
        assertTrue(jdbcTemplate.firstSqlContaining("limit 2")
                .contains("from road_route r"));
    }

    @Test
    public void queryRouteSummaryDoesNotInventTargetWhenRouteIsUnresolved() throws Exception {
        MapRegionSummaryTool tool = new MapRegionSummaryTool();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        jdbcTemplate.enqueueList(new ArrayList<Map<String, Object>>());
        setField(tool, "namedParameterJdbcTemplate", jdbcTemplate);

        MapAiContext mapContext = new MapAiContext();
        mapContext.setTenantId("tenant-a");
        mapContext.setMode("ROUTE");
        mapContext.setRouteCode("MISSING");
        mapContext.setExtra(mapOf(
                "rawContext", mapOf("query", mapOf("projectId", "project-a"))
        ));
        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");
        context.setMapContext(mapContext);

        AiToolResult result = tool.execute(context, mapOf("limit", 50));

        assertTrue(result.isSuccess());
        Map<?, ?> scope = (Map<?, ?>) ((Map<?, ?>) result.getData()).get("queryScope");
        assertEquals(null, scope.get("objectId"));
        assertEquals(null, scope.get("objectType"));
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
        private final java.util.Deque<List<Map<String, Object>>> listResponses =
                new java.util.ArrayDeque<>();
        private SqlParameterSource lastParams;

        CapturingJdbcTemplate() {
            super(new DriverManagerDataSource());
        }

        void enqueueList(List<Map<String, Object>> rows) {
            listResponses.addLast(rows);
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
            return listResponses.isEmpty()
                    ? new ArrayList<Map<String, Object>>()
                    : listResponses.removeFirst();
        }

        String joinedSql() {
            StringBuilder sb = new StringBuilder();
            for (String sql : sqlList) {
                sb.append(sql).append('\n');
            }
            return sb.toString();
        }

        String sqlAt(int index) {
            return sqlList.get(index);
        }

        String firstSqlContaining(String fragment) {
            for (String sql : sqlList) {
                if (sql.contains(fragment)) {
                    return sql;
                }
            }
            return "";
        }
    }
}
