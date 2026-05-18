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
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
