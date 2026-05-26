package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionService;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SolutionGenerateToolTest {

    @Test
    public void objectSolutionDoesNotForceGeneralAdviceWhenSolutionTypeIsMissing() throws Exception {
        SolutionGenerateTool tool = new SolutionGenerateTool();
        CapturingMapObjectSolutionService service = new CapturingMapObjectSolutionService();
        setField(tool, "mapObjectSolutionService", service);

        MapAiContext mapContext = new MapAiContext();
        mapContext.setMode("OBJECT");
        mapContext.setRouteCode("Y016140727");
        mapContext.setYear(2026);
        mapContext.setMapObject(mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-good",
                "routeCode", "Y016140727",
                "year", 2026,
                "mqi", 85.067,
                "activeMetricGrade", "GOOD",
                "activeMetricValue", 85.067
        ));

        AiToolContext context = new AiToolContext();
        context.setTenantId("default");
        context.setMapContext(mapContext);
        context.setOptions(new LinkedHashMap<String, Object>());

        AiToolResult result = tool.execute(context, mapOf("action", "GENERATE_OBJECT_SOLUTION"));

        assertTrue(result.isSuccess());
        assertEquals("ASSESSMENT_RESULT", service.lastRequest.getObjectType());
        assertNull(service.lastRequest.getSolutionType());
    }

    @Test
    public void objectSolutionPrefersSelectedObjectRouteOverConflictingArgs() throws Exception {
        SolutionGenerateTool tool = new SolutionGenerateTool();
        CapturingMapObjectSolutionService service = new CapturingMapObjectSolutionService();
        setField(tool, "mapObjectSolutionService", service);

        MapAiContext mapContext = new MapAiContext();
        mapContext.setMode("OBJECT");
        mapContext.setRouteCode("G210");
        mapContext.setYear(2026);
        mapContext.setMapObject(mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-good",
                "routeCode", "Y016140727",
                "year", 2026,
                "mqi", 85.067
        ));

        AiToolContext context = new AiToolContext();
        context.setTenantId("default");
        context.setMapContext(mapContext);
        context.setOptions(new LinkedHashMap<String, Object>());

        AiToolResult result = tool.execute(context, mapOf(
                "action", "GENERATE_OBJECT_SOLUTION",
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-good",
                "routeCode", "G210"
        ));

        assertTrue(result.isSuccess());
        assertEquals("Y016140727", service.lastRequest.getRouteCode());
        assertEquals("Y016140727", service.lastRequest.getMapObject().get("routeCode"));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
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

    private static class CapturingMapObjectSolutionService implements MapObjectSolutionService {
        private MapObjectSolutionRequest lastRequest;

        @Override
        public MapObjectSolutionResponse generate(MapObjectSolutionRequest request) {
            this.lastRequest = request;
            MapObjectSolutionResponse response = new MapObjectSolutionResponse();
            response.setSolutionType("EVALUATION_UNIT_ADVICE");
            response.setTitle("评定养护建议");
            response.setMarkdown("评定养护建议");
            return response;
        }
    }
}
