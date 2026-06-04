package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.junit.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GisMapSupportServiceImplTest {

    @Test
    public void mapStatisticsUsesSectionLengthForRouteTotalLength() throws Exception {
        GisMapSupportServiceImpl service = new GisMapSupportServiceImpl();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbcTemplate);
        TenantContextHolder.setTenantId("tenant-a");

        try {
            service.mapStatistics(mapOf(
                    "routeCode", "Y016140727",
                    "projectId", "project-a",
                    "sectionTier", "LINE",
                    "year", 2026
            ));
        } finally {
            TenantContextHolder.clear();
        }

        String totalLengthSql = jdbcTemplate.sqlAt(0);
        assertTrue(totalLengthSql.contains("from road_section_line"));
        assertTrue(totalLengthSql.contains("sum(coalesce(length_km"));
        assertFalse(totalLengthSql.contains("from road_route"));
    }

    @Test
    public void mapStatisticsAppliesStakeRangeToObjectScopedCounts() throws Exception {
        GisMapSupportServiceImpl service = new GisMapSupportServiceImpl();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbcTemplate);
        TenantContextHolder.setTenantId("tenant-a");

        try {
            service.mapStatistics(mapOf(
                    "routeCode", "Y016140727",
                    "projectId", "project-a",
                    "sectionTier", "LINE",
                    "year", 2026,
                    "stakeStart", 10.0,
                    "stakeEnd", 12.5
            ));
        } finally {
            TenantContextHolder.clear();
        }

        assertStakeOverlapSql(jdbcTemplate.sqlAt(0), "road_section_line");
        assertStakeOverlapSql(jdbcTemplate.sqlAt(2), "road_section_line");
        assertStakeOverlapSql(jdbcTemplate.sqlAt(3), "road_section_ledger");
        assertStakeOverlapSql(jdbcTemplate.sqlAt(4), "disease_record");
        assertStakeOverlapSql(jdbcTemplate.sqlAt(6), "assessment_result");
        assertNotNull(jdbcTemplate.paramAt(0).getValue("stakeStart"));
        assertNotNull(jdbcTemplate.paramAt(0).getValue("stakeEnd"));
    }

    @Test
    public void routeObjectDetailUsesSectionLengthForDisplayedLength() throws Exception {
        GisMapSupportServiceImpl service = new GisMapSupportServiceImpl();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbcTemplate);
        TenantContextHolder.setTenantId("tenant-a");

        try {
            service.objectDetail("ROAD_ROUTE", "route-1");
        } finally {
            TenantContextHolder.clear();
        }

        String sql = jdbcTemplate.sqlAt(0);
        assertTrue(sql.contains("road_section_line"));
        assertTrue(sql.contains("coalesce(sec.length_km"));
    }

    @Test
    public void spatialRouteLayerUsesSectionLengthForFeatureLength() throws Exception {
        GisMapSupportServiceImpl service = new GisMapSupportServiceImpl();
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbcTemplate);

        service.spatialQuery(mapOf(
                "geometry", mapOf(
                        "type", "Polygon",
                        "coordinates", Collections.singletonList(Arrays.asList(
                                Arrays.asList(112.0, 37.0),
                                Arrays.asList(113.0, 37.0),
                                Arrays.asList(113.0, 38.0),
                                Arrays.asList(112.0, 37.0)
                        ))
                ),
                "projectId", "project-a",
                "query", mapOf("routeCode", "Y016140727"),
                "layers", Collections.singletonList("ROAD_ROUTE")
        ));

        String sql = jdbcTemplate.sqlAt(0);
        assertTrue(sql.contains("road_section_line"));
        assertTrue(sql.contains("coalesce(sec.length_km"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = GisMapSupportServiceImpl.class.getDeclaredField(fieldName);
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

    private static void assertStakeOverlapSql(String sql, String tableName) {
        assertTrue(sql.contains("from " + tableName));
        assertTrue(sql.contains(":stakeStart"));
        assertTrue(sql.contains(":stakeEnd"));
        assertTrue(sql.contains("start_stake"));
        assertTrue(sql.contains("end_stake"));
    }

    private static class CapturingJdbcTemplate extends NamedParameterJdbcTemplate {
        private final List<String> sqlList = new ArrayList<>();
        private final List<SqlParameterSource> paramsList = new ArrayList<>();

        CapturingJdbcTemplate() {
            super(new DriverManagerDataSource());
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource paramSource, Class<T> requiredType) {
            sqlList.add(sql);
            paramsList.add(paramSource);
            Object value = 1;
            return requiredType.cast(value);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, SqlParameterSource paramSource) {
            sqlList.add(sql);
            paramsList.add(paramSource);
            return new ArrayList<Map<String, Object>>();
        }

        String sqlAt(int index) {
            return sqlList.get(index);
        }

        SqlParameterSource paramAt(int index) {
            return paramsList.get(index);
        }
    }
}
