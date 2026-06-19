package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.gis.dto.SourceBindingVerifyRequest;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SourceBindingVerifyServiceImplTest {

    private SourceBindingVerifyServiceImpl service;
    private CapturingJdbcTemplate jdbc;

    @Before
    public void setUp() throws Exception {
        service = new SourceBindingVerifyServiceImpl();
        jdbc = new CapturingJdbcTemplate();
        setField(service, "namedParameterJdbcTemplate", jdbc);
        TenantContextHolder.setTenantId("tenant-a");
    }

    @After
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    public void objectBindingRequiresProjectAndExactObjectId() {
        jdbc.enqueue(Collections.singletonList(mapOf(
                "id", "disease-1",
                "route_code", "Y016140727",
                "start_stake", new BigDecimal("1.200"),
                "end_stake", new BigDecimal("1.250")
        )));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "DISEASE",
                        "objectId", "disease-1",
                        "routeCode", "Y016140727"
                )
        ));

        assertEquals("VALID", result.get("bindingStatus"));
        assertEquals("disease", result.get("recommendedLayer"));
        assertEquals(1, result.get("matchedCount"));
        assertEquals(
                "disease-1",
                ((Map<?, ?>) result.get("resolvedTarget")).get("objectId")
        );
        assertTrue(jdbc.sqlAt(0).contains("id=:objectId"));
        assertTrue(jdbc.sqlAt(0).contains("project_id=:projectId"));
        assertEquals("project-a", jdbc.paramsAt(0).getValue("projectId"));
    }

    @Test
    public void objectBindingReturnsNotFoundWithoutRouteFallback() {
        jdbc.enqueue(Collections.<Map<String, Object>>emptyList());

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "DISEASE",
                        "objectId", "missing",
                        "routeCode", "Y016140727"
                )
        ));

        assertEquals("NOT_FOUND", result.get("bindingStatus"));
        assertEquals(0, result.get("matchedCount"));
        assertEquals(1, jdbc.sqlCount());
    }

    @Test
    public void objectBindingReturnsInvalidOnRouteConflict() {
        jdbc.enqueue(Collections.singletonList(mapOf(
                "id", "disease-1",
                "route_code", "G210",
                "start_stake", new BigDecimal("1.200"),
                "end_stake", new BigDecimal("1.250")
        )));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "DISEASE",
                        "objectId", "disease-1",
                        "routeCode", "Y016140727"
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertTrue(String.valueOf(result.get("bindingReason")).contains("路线"));
    }

    @Test
    public void objectBindingReturnsInvalidOnNonOverlappingStakeConflict() {
        jdbc.enqueue(Collections.singletonList(mapOf(
                "id", "section-1",
                "route_code", "Y016140727",
                "start_stake", new BigDecimal("10.000"),
                "end_stake", new BigDecimal("12.000")
        )));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "ROAD_SECTION",
                        "objectId", "section-1",
                        "routeCode", "Y016140727",
                        "startStake", 20,
                        "endStake", 21
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertTrue(String.valueOf(result.get("bindingReason")).contains("桩号"));
    }

    @Test
    public void routeStakeRangeReturnsNotFoundWhenProjectHasNoMatchingRows() {
        jdbc.enqueue(Collections.singletonList(mapOf("matched_count", 0)));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf(
                        "routeCode", "Y016140727",
                        "startStake", 1.2,
                        "endStake", 1.4
                )
        ));

        assertEquals("NOT_FOUND", result.get("bindingStatus"));
        assertEquals("roadSection", result.get("recommendedLayer"));
        assertEquals(0, result.get("matchedCount"));
        assertTrue(jdbc.sqlAt(0).contains("road_section_line"));
        assertTrue(jdbc.sqlAt(0).contains("start_stake"));
        assertTrue(jdbc.sqlAt(0).contains("end_stake"));
    }

    @Test
    public void routeStakeRangeReturnsValidWithResolvedRange() {
        jdbc.enqueue(Collections.singletonList(mapOf("matched_count", 3)));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf(
                        "routeCode", "Y016140727",
                        "startStake", 1.0,
                        "endStake", 2.0
                )
        ));

        assertEquals("VALID", result.get("bindingStatus"));
        assertEquals(3, result.get("matchedCount"));
        Map<?, ?> target = (Map<?, ?>) result.get("resolvedTarget");
        assertEquals(new BigDecimal("1.0"), target.get("startStake"));
        assertEquals(new BigDecimal("2.0"), target.get("endStake"));
    }

    @Test
    public void reversedStakeRangeIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf(
                        "routeCode", "Y016140727",
                        "startStake", 2.0,
                        "endStake", 1.0
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    @Test
    public void malformedBboxIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf("bbox", Arrays.asList(112.0, 37.0))
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    @Test
    public void malformedGeometryIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf(
                        "geometry",
                        mapOf(
                                "type", "LineString",
                                "coordinates", Collections.singletonList(
                                        Arrays.asList(112.0, 37.0)
                                )
                        )
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    @Test
    public void unknownGeometryTypeIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf(
                        "geometry",
                        mapOf(
                                "type", "Envelope",
                                "coordinates", Arrays.asList(
                                        Arrays.asList(112.0, 37.0),
                                        Arrays.asList(113.0, 38.0)
                                )
                        )
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    @Test
    public void nonStringRouteCodeIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf(
                        "routeCode", 123,
                        "startStake", 1,
                        "endStake", 2
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    @Test
    public void nonStringObjectIdIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "DISEASE",
                        "objectId", 123
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    @Test
    public void geometryRangeUsesProjectScopedSpatialExistenceQuery() {
        jdbc.enqueue(Collections.singletonList(mapOf("matched_count", 2)));
        Map<String, Object> geometry = mapOf(
                "type", "Polygon",
                "coordinates", Collections.singletonList(Arrays.asList(
                        Arrays.asList(112.0, 37.0),
                        Arrays.asList(113.0, 37.0),
                        Arrays.asList(113.0, 38.0),
                        Arrays.asList(112.0, 37.0)
                ))
        );

        Map<String, Object> result = service.verify(request(
                "project-a",
                "RANGE",
                mapOf("geometry", geometry)
        ));

        assertEquals("VALID", result.get("bindingStatus"));
        assertEquals(2, result.get("matchedCount"));
        assertEquals("roadSection", result.get("recommendedLayer"));
        assertTrue(jdbc.sqlAt(0).contains("ST_Intersects"));
        assertTrue(jdbc.sqlAt(0).contains("project_id=:projectId"));
    }

    @Test
    public void assessmentObjectUsesRouteProjectMembership() {
        jdbc.enqueue(Collections.singletonList(mapOf(
                "id", "assessment-1",
                "route_code", "Y016140727",
                "start_stake", BigDecimal.ZERO,
                "end_stake", new BigDecimal("0.100")
        )));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "ASSESSMENT_RESULT",
                        "objectId", "assessment-1"
                )
        ));

        assertEquals("VALID", result.get("bindingStatus"));
        assertTrue(jdbc.sqlAt(0).contains("assessment_result"));
        assertTrue(jdbc.sqlAt(0).contains("road_route"));
        assertFalse(jdbc.sqlAt(0).contains("assessment_result.project_id"));
    }

    @Test
    public void roadRouteObjectUsesDirectProjectMembership() {
        jdbc.enqueue(Collections.singletonList(mapOf(
                "id", "route-1",
                "route_code", "Y016140727",
                "start_stake", BigDecimal.ZERO,
                "end_stake", new BigDecimal("14.072")
        )));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "ROAD_ROUTE",
                        "objectId", "route-1"
                )
        ));

        assertEquals("VALID", result.get("bindingStatus"));
        assertEquals("roadRoute", result.get("recommendedLayer"));
        assertTrue(jdbc.sqlAt(0).contains("from road_route"));
        assertTrue(jdbc.sqlAt(0).contains("project_id=:projectId"));
    }

    @Test
    public void evaluationUnitUsesOwnOrRouteProjectMembership() {
        jdbc.enqueue(Collections.singletonList(mapOf(
                "id", "unit-1",
                "route_code", "Y016140727",
                "start_stake", BigDecimal.ZERO,
                "end_stake", new BigDecimal("1.000")
        )));

        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "EVALUATION_UNIT",
                        "objectId", "unit-1"
                )
        ));

        assertEquals("VALID", result.get("bindingStatus"));
        assertEquals("roadSection", result.get("recommendedLayer"));
        assertTrue(jdbc.sqlAt(0).contains("road_section_ledger"));
        assertTrue(jdbc.sqlAt(0).contains("u.project_id=:projectId"));
        assertTrue(jdbc.sqlAt(0).contains("road_route"));
    }

    @Test
    public void unsupportedObjectTypeIsInvalidWithoutDatabaseQuery() {
        Map<String, Object> result = service.verify(request(
                "project-a",
                "OBJECT",
                mapOf(
                        "objectType", "CURRENT_SELECTION",
                        "objectId", "selection-1"
                )
        ));

        assertEquals("INVALID", result.get("bindingStatus"));
        assertEquals(0, jdbc.sqlCount());
    }

    private static SourceBindingVerifyRequest request(
            String projectId,
            String bindingType,
            Map<String, Object> target
    ) {
        SourceBindingVerifyRequest request = new SourceBindingVerifyRequest();
        request.setProjectId(projectId);
        request.setBindingType(bindingType);
        request.setMapTarget(target);
        return request;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SourceBindingVerifyServiceImpl.class.getDeclaredField(fieldName);
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
        private final List<String> sql = new ArrayList<>();
        private final List<SqlParameterSource> params = new ArrayList<>();
        private final Deque<List<Map<String, Object>>> responses = new ArrayDeque<>();

        CapturingJdbcTemplate() {
            super(new DriverManagerDataSource());
        }

        void enqueue(List<Map<String, Object>> rows) {
            responses.addLast(rows);
        }

        @Override
        public List<Map<String, Object>> queryForList(
                String sql,
                SqlParameterSource paramSource
        ) {
            this.sql.add(sql);
            this.params.add(paramSource);
            return responses.isEmpty()
                    ? Collections.<Map<String, Object>>emptyList()
                    : responses.removeFirst();
        }

        int sqlCount() {
            return sql.size();
        }

        String sqlAt(int index) {
            return sql.get(index);
        }

        SqlParameterSource paramsAt(int index) {
            return params.get(index);
        }
    }
}
