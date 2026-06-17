package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeStatsResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.rag.RagQueryRewriteService;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KnowledgeRetrieveToolTest {

    @Test
    public void executeReturnsRetrievalRequestSummaryForDiagnostics() throws Exception {
        KnowledgeRetrieveTool tool = new KnowledgeRetrieveTool();
        CapturingRetrieverService retrieverService = new CapturingRetrieverService();
        setField(tool, "aiKnowledgeRetrieverService", retrieverService);
        setField(tool, "ragQueryRewriteService", new EchoRewriteService());

        AiToolContext context = new AiToolContext();
        context.setTenantId("tenant-a");
        MapAiContext mapContext = new MapAiContext();
        mapContext.setRouteCode("Y016140727");
        context.setMapContext(mapContext);
        context.setOptions(mapOf("topK", 3));

        AiToolResult result = tool.execute(context, mapOf(
                "query", "解释 PCI 指标",
                "filters", mapOf("capabilityIds", Arrays.asList("knowledge.metric_explain"))
        ));

        assertTrue(result.isSuccess());
        AiKnowledgeSearchResponse data = (AiKnowledgeSearchResponse) result.getData();
        Map request = data.getRequest();
        assertEquals("解释 PCI 指标", request.get("query"));
        assertEquals(3, request.get("topK"));
        assertEquals(Arrays.asList("knowledge.metric_explain"), ((Map) request.get("filters")).get("capabilityIds"));
        assertEquals("tenant-a", retrieverService.lastRequest.getTenantId());
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

    private static class CapturingRetrieverService implements AiKnowledgeRetrieverService {
        private AiKnowledgeSearchRequest lastRequest;

        @Override
        public AiKnowledgeSearchResponse search(AiKnowledgeSearchRequest request) {
            this.lastRequest = request;
            AiKnowledgeSearchResponse response = new AiKnowledgeSearchResponse();
            response.setSearchMode("VECTOR");
            response.setRetrievalStrategy("VECTOR");
            response.setHitCount(0);
            return response;
        }

        @Override
        public AiKnowledgeStatsResponse stats(String tenantId) {
            return new AiKnowledgeStatsResponse();
        }
    }

    private static class EchoRewriteService implements RagQueryRewriteService {
        @Override
        public String rewrite(String userQuestion, MapAiContext context) {
            return userQuestion;
        }
    }
}
