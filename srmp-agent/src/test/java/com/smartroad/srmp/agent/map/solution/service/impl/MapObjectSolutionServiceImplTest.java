package com.smartroad.srmp.agent.map.solution.service.impl;

import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.map.MapObjectContext;
import com.smartroad.srmp.agent.map.MapObjectContextService;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionQualityChecker;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapObjectSolutionServiceImplTest {

    @Test
    public void generateUsesLlmAndReturnsAnswerMetaWhenRequireAi() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        setField(service, "mapObjectContextService", new FakeMapObjectContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", new FakeTemplatePipelineService());
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", new FakeLlmClient("LLM 结构化低分处置建议"));

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setTenantId("default");
        request.setObjectType("ASSESSMENT_RESULT");
        request.setObjectId("assessment-1");
        request.setRouteCode("Y016140727");
        request.setYear(2026);
        request.setSolutionType("LOW_SCORE_TREATMENT");
        request.setMapObject(assessmentObject());
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("requireAi", true);
        request.setOptions(options);

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("LLM 结构化低分处置建议", response.getMarkdown());
        assertEquals("LLM", response.getAnswerMeta().get("answerSource"));
        assertEquals("SUCCESS", response.getAnswerMeta().get("llmStatus"));
        assertEquals(Boolean.TRUE, response.getAnswerMeta().get("llmSuccess"));
        assertEquals(Boolean.FALSE, response.getAnswerMeta().get("fallback"));
        assertTrue(response.getTrace().toString().contains("map_object_llm_generate"));
    }

    @Test
    public void generateFallsBackToRequestMapObjectWhenContextLookupFails() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        setField(service, "mapObjectContextService", new ThrowingMapObjectContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", new FakeTemplatePipelineService());
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", new FakeLlmClient(""));

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setObjectType("ASSESSMENT_RESULT");
        request.setObjectId("assessment-1");
        request.setRouteCode("Y016140727");
        request.setYear(2026);
        request.setSolutionType("LOW_SCORE_TREATMENT");
        request.setMapObject(assessmentObject());

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("LOW_SCORE_TREATMENT", response.getSolutionType());
        assertEquals("模板低分处置建议", response.getMarkdown());
        assertEquals("Y016140727", response.getObjectSummary().get("routeCode"));
    }

    @Test
    public void goodAssessmentDefaultsToNeutralEvaluationAdvice() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        setField(service, "mapObjectContextService", new FakeMapObjectContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", new PassthroughTemplatePipelineService());
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", new FakeLlmClient(""));

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setObjectType("ASSESSMENT_RESULT");
        request.setObjectId("assessment-good");
        request.setRouteCode("Y016140727");
        request.setYear(2026);
        request.setMapObject(mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-good",
                "routeCode", "Y016140727",
                "year", 2026,
                "startStake", 0,
                "endStake", 14.072,
                "mqi", 85.067,
                "pqi", 75.111,
                "pci", 77.614,
                "activeMetricGrade", "GOOD",
                "activeMetricValue", 85.067
        ));

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("EVALUATION_UNIT_ADVICE", response.getSolutionType());
        assertFalse(response.getTitle().contains("低分"));
        assertFalse(response.getMarkdown().contains("低分单元"));
        assertTrue(response.getMarkdown().contains("评定单元") || response.getMarkdown().contains("评定对象"));
    }

    @Test
    public void poorAssessmentDefaultsToLowScoreTreatment() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        setField(service, "mapObjectContextService", new FakeMapObjectContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", new PassthroughTemplatePipelineService());
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", new FakeLlmClient(""));

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setObjectType("ASSESSMENT_RESULT");
        request.setObjectId("assessment-bad");
        request.setRouteCode("Y016140727");
        request.setYear(2026);
        request.setMapObject(mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "assessment-bad",
                "routeCode", "Y016140727",
                "year", 2026,
                "startStake", 0,
                "endStake", 14.072,
                "mqi", 58.2,
                "pqi", 63.0,
                "pci", 59.8,
                "activeMetricGrade", "BAD",
                "activeMetricValue", 58.2
        ));

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("LOW_SCORE_TREATMENT", response.getSolutionType());
        assertTrue(response.getTitle().contains("低分"));
        assertTrue(response.getMarkdown().contains("低分单元"));
    }

    @Test
    public void generateCarriesBusinessEvidenceIntoTemplateAndLlmPrompt() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        CapturingTemplatePipelineService templatePipelineService = new CapturingTemplatePipelineService();
        CapturingLlmClient llmClient = new CapturingLlmClient("LLM 路段计划");
        setField(service, "mapObjectContextService", new FakeMapObjectContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", templatePipelineService);
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", llmClient);

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setTenantId("default");
        request.setObjectType("ROAD_SECTION");
        request.setObjectId("section-1");
        request.setRouteCode("Y016140727");
        request.setYear(2026);
        request.setSolutionType("SECTION_PLAN");
        request.setMapObject(mapOf(
                "objectType", "ROAD_SECTION",
                "objectId", "section-1",
                "routeCode", "Y016140727",
                "startStake", 0,
                "endStake", 14.072
        ));
        request.setBusinessEvidence(mapOf(
                "toolSuccessCount", 3,
                "businessHitCount", 6,
                "toolSummary", Arrays.asList(
                        mapOf("toolName", "gis.queryAssessmentResults", "hitCount", 2, "summary", "查询到 2 条评定结果"),
                        mapOf("toolName", "gis.queryDiseases", "hitCount", 2, "summary", "查询到 2 条病害记录"),
                        mapOf("toolName", "gis.queryDiseasesByStakeRange", "hitCount", 2, "summary", "查询到评定单元内病害 2 条")
                )
        ));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("requireAi", true);
        request.setOptions(options);

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("LLM 路段计划", response.getMarkdown());
        assertTrue(templatePipelineService.lastContext.getBusinessData().containsKey("businessEvidence"));
        assertTrue(llmClient.lastUserPrompt.contains("gis.queryDiseases"));
        assertTrue(llmClient.lastUserPrompt.contains("查询到 2 条病害记录"));
    }

    @Test
    public void routeReportSummaryPrefersRequestedRouteExtentOverLookupSlice() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        setField(service, "mapObjectContextService", new ConflictingRouteLookupContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", new PassthroughTemplatePipelineService());
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", new FakeLlmClient(""));

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setTenantId("default");
        request.setObjectType("ROAD_ROUTE");
        request.setObjectId("route-Y016140727");
        request.setRouteCode("Y016140727");
        request.setYear(2026);
        request.setSolutionType("ROUTE_REPORT");
        request.setMapObject(mapOf(
                "objectType", "ROAD_ROUTE",
                "objectId", "route-Y016140727",
                "id", "route-Y016140727",
                "routeCode", "Y016140727",
                "routeName", "峪口村--上庄线",
                "startStake", 0,
                "endStake", 14.072
        ));

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("K0-K14.072", response.getObjectSummary().get("stakeRange"));
        assertEquals("14.072", String.valueOf(response.getObjectSummary().get("lengthKm")));
        assertTrue(String.valueOf(response.getObjectSummary().get("contextSummary")).contains("14.072 km"));
        assertFalse(String.valueOf(response.getObjectSummary().get("contextSummary")).contains("0.926 km"));
        assertFalse(response.getMarkdown().contains("0.926 km"));
    }

    @Test
    public void routeReportTitleUsesRouteScopeWhenNoSingleRouteCode() throws Exception {
        MapObjectSolutionServiceImpl service = new MapObjectSolutionServiceImpl();
        setField(service, "mapObjectContextService", new FakeMapObjectContextService());
        setField(service, "qualityChecker", new MapObjectSolutionQualityChecker());
        setField(service, "templatePipelineService", new PassthroughTemplatePipelineService());
        setField(service, "aiTraceService", new FakeAiTraceService());
        setField(service, "llmClient", new FakeLlmClient(""));

        MapObjectSolutionRequest request = new MapObjectSolutionRequest();
        request.setTenantId("default");
        request.setObjectType("ROAD_ROUTE");
        request.setSolutionType("ROUTE_REPORT");
        request.setMapObject(mapOf("objectType", "ROAD_ROUTE"));

        MapObjectSolutionResponse response = service.generate(request);

        assertEquals("当前路线范围路线技术状况报告草稿", response.getTitle());
        assertFalse(response.getTitle().contains("当前对象"));
    }

    private Map<String, Object> assessmentObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("objectType", "ASSESSMENT_RESULT");
        map.put("objectId", "assessment-1");
        map.put("routeCode", "Y016140727");
        map.put("year", 2026);
        map.put("startStake", 0);
        map.put("endStake", 14.072);
        map.put("mqi", 85.067);
        map.put("pqi", 75.111);
        map.put("pci", 77.614);
        return map;
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

    private static class FakeMapObjectContextService implements MapObjectContextService {
        @Override
        public MapObjectContext resolve(Map context) {
            Map wrapper = context == null ? new LinkedHashMap() : context;
            Object raw = wrapper.get("mapObject");
            return raw instanceof Map ? MapObjectContext.of((Map) raw) : MapObjectContext.empty();
        }

        @Override
        public Map getObjectDetail(String objectType, String objectId, String routeCode, Integer year) {
            return new LinkedHashMap();
        }
    }

    private static class ConflictingRouteLookupContextService implements MapObjectContextService {
        @Override
        public MapObjectContext resolve(Map context) {
            Map raw = context == null || !(context.get("mapObject") instanceof Map)
                    ? new LinkedHashMap()
                    : (Map) context.get("mapObject");
            Map merged = new LinkedHashMap();
            merged.putAll(raw);
            merged.putAll(mapOf(
                    "object_type", "ROAD_ROUTE",
                    "id", "route-db-slice",
                    "route_code", "Y016140727",
                    "route_name", "峪口村--上庄线",
                    "start_stake", 13.146,
                    "end_stake", 14.072,
                    "length_km", 0.926,
                    "context_summary", "路线 Y016140727 峪口村--上庄线，里程 0.926 km"
            ));
            merged.put("objectType", "ROAD_ROUTE");
            merged.put("objectId", raw.get("objectId"));
            merged.put("routeCode", raw.get("routeCode"));
            merged.put("year", raw.get("year"));
            return MapObjectContext.of(merged);
        }

        @Override
        public Map getObjectDetail(String objectType, String objectId, String routeCode, Integer year) {
            return new LinkedHashMap();
        }
    }

    private static class ThrowingMapObjectContextService implements MapObjectContextService {
        @Override
        public MapObjectContext resolve(Map context) {
            throw new IllegalStateException("assessment_result table missing");
        }

        @Override
        public Map getObjectDetail(String objectType, String objectId, String routeCode, Integer year) {
            throw new IllegalStateException("assessment_result table missing");
        }
    }

    private static class FakeTemplatePipelineService implements AiSolutionTemplatePipelineService {
        @Override
        public TemplatePipelineResult generate(SolutionTemplateContext context) {
            TemplatePipelineResult result = new TemplatePipelineResult();
            result.setMarkdown("模板低分处置建议");
            Map<String, Object> templateMeta = new LinkedHashMap<>();
            templateMeta.put("fallback", true);
            templateMeta.put("templateCode", "SYSTEM_FALLBACK_LOW_SCORE_TREATMENT");
            result.setTemplateMeta(templateMeta);
            return result;
        }
    }

    private static class CapturingTemplatePipelineService implements AiSolutionTemplatePipelineService {
        private SolutionTemplateContext lastContext;

        @Override
        public TemplatePipelineResult generate(SolutionTemplateContext context) {
            this.lastContext = context;
            TemplatePipelineResult result = new TemplatePipelineResult();
            result.setMarkdown("模板路段计划");
            Map<String, Object> templateMeta = new LinkedHashMap<>();
            templateMeta.put("fallback", false);
            templateMeta.put("templateCode", "SECTION_PLAN_TEMPLATE");
            result.setTemplateMeta(templateMeta);
            return result;
        }
    }

    private static class PassthroughTemplatePipelineService implements AiSolutionTemplatePipelineService {
        @Override
        public TemplatePipelineResult generate(SolutionTemplateContext context) {
            TemplatePipelineResult result = new TemplatePipelineResult();
            result.setMarkdown(context.getFallbackMarkdown());
            Map<String, Object> templateMeta = new LinkedHashMap<>();
            templateMeta.put("fallback", true);
            templateMeta.put("templateCode", "SYSTEM_FALLBACK_" + context.getSolutionType());
            result.setTemplateMeta(templateMeta);
            return result;
        }
    }

    private static class FakeLlmClient implements LlmClient {
        private final String answer;

        FakeLlmClient(String answer) {
            this.answer = answer;
        }

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            return answer;
        }
    }

    private static class CapturingLlmClient implements LlmClient {
        private final String answer;
        private String lastUserPrompt;

        CapturingLlmClient(String answer) {
            this.answer = answer;
        }

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            this.lastUserPrompt = userPrompt;
            return answer;
        }
    }

    private static class FakeAiTraceService implements AiTraceService {
        @Override
        public void save(AiTraceContext trace) {
        }

        @Override
        public List<Map<String, Object>> list(String status, String keyword, Integer limit) {
            return new ArrayList<>();
        }

        @Override
        public Map<String, Object> detail(String traceId) {
            return new LinkedHashMap<>();
        }

        @Override
        public List<Map<String, Object>> steps(String traceId) {
            return new ArrayList<>();
        }
    }
}
