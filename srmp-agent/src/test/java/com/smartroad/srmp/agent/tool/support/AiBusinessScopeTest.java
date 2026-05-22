package com.smartroad.srmp.agent.tool.support;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiBusinessScopeTest {

    @Test
    public void extractsLatestMapBusinessScopeFromNestedContextAndArgs() {
        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");

        MapAiContext mapContext = new MapAiContext();
        mapContext.setMode("OBJECT");
        mapContext.setRouteCode("Y016140727");
        mapContext.setYear(2026);
        mapContext.setSelectedLayers(Arrays.asList("ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"));
        mapContext.setMapObject(mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-1",
                "routeCode", "Y016140727",
                "startStake", 0,
                "endStake", 14.072,
                "raw", mapOf(
                        "object_type", "ROAD_SECTION_LEDGER",
                        "direction", "UP",
                        "start_stake", 0,
                        "end_stake", 14.072
                )
        ));
        mapContext.setExtra(mapOf(
                "rawContext", mapOf(
                        "query", mapOf(
                                "projectId", "project-2026",
                                "sectionTier", "LEDGER"
                        )
                )
        ));
        context.setMapContext(mapContext);

        AiBusinessScope scope = AiBusinessScope.from(context, mapOf(
                "objectId", "assessment-1",
                "objectType", "ASSESSMENT_RESULT"
        ));

        assertEquals("tenant-a", scope.getTenantId());
        assertEquals("project-2026", scope.getProjectId());
        assertEquals("Y016140727", scope.getRouteCode());
        assertEquals(Integer.valueOf(2026), scope.getYear());
        assertEquals("LEDGER", scope.getSectionTier());
        assertEquals("OBJECT", scope.getContextScope());
        assertEquals("ASSESSMENT_RESULT", scope.getObjectType());
        assertEquals("assessment-1", scope.getObjectId());
        assertEquals("ROAD_SECTION_LEDGER", scope.getAssessmentObjectType());
        assertEquals("UP", scope.getDirection());
        assertEquals(Double.valueOf(0), scope.getStartStake());
        assertEquals(Double.valueOf(14.072), scope.getEndStake());
        assertEquals(3, scope.getSelectedLayers().size());
        assertTrue(scope.getScopeWarnings().isEmpty());
        assertEquals("project-2026", scope.toQueryScope().get("projectId"));
    }

    @Test
    public void warnsWhenProjectBoundMapContextHasNoProjectId() {
        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");
        MapAiContext mapContext = new MapAiContext();
        mapContext.setMode("OBJECT");
        mapContext.setRouteCode("G210");
        mapContext.setExtra(mapOf(
                "rawContext", mapOf(
                        "query", mapOf("sectionTier", "LINE")
                )
        ));
        context.setMapContext(mapContext);

        AiBusinessScope scope = AiBusinessScope.from(context, new LinkedHashMap<String, Object>());

        assertEquals("G210", scope.getRouteCode());
        assertEquals("LINE", scope.getSectionTier());
        assertTrue(scope.getScopeWarnings().contains("PROJECT_ID_MISSING"));
    }

    @Test
    public void objectScopePrefersSelectedObjectRouteOverConflictingRouteArgs() {
        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");

        MapAiContext mapContext = new MapAiContext();
        mapContext.setMode("OBJECT");
        mapContext.setRouteCode("G210");
        mapContext.setYear(2026);
        mapContext.setMapObject(mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-1",
                "routeCode", "Y016140727",
                "startStake", 0,
                "endStake", 14.072
        ));
        context.setMapContext(mapContext);

        AiBusinessScope scope = AiBusinessScope.from(context, mapOf(
                "routeCode", "G210",
                "objectId", "assessment-1",
                "objectType", "ASSESSMENT_RESULT"
        ));

        assertEquals("Y016140727", scope.getRouteCode());
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
