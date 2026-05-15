package com.smartroad.srmp.agent.orchestrator.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RemoteLangGraphOrchestratorContractTest {

    @Test
    public void mapAiAgentResponseAcceptsExtendedLangGraphKnowledgeSourceFields() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", "已生成分析");

        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", "kg-001");
        source.put("raw", singletonMap("source", "langgraph"));
        source.put("chunkId", "chunk-001");
        source.put("documentId", "doc-001");
        source.put("title", "公路养护规范");
        source.put("sourceType", "KNOWLEDGE");
        source.put("sourceId", "kg-001");
        source.put("content", "裂缝处治建议");
        source.put("score", 0.82);
        sources.add(source);
        body.put("knowledgeSources", sources);

        MapAiAgentResponse response = new ObjectMapper().convertValue(body, MapAiAgentResponse.class);

        assertEquals("已生成分析", response.getAnswer());
        assertEquals(1, response.getKnowledgeSources().size());
        assertEquals("chunk-001", response.getKnowledgeSources().get(0).getChunkId());
    }

    @Test
    public void mapAgentRunResponseAcceptsRuntimePlanExecution() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", "已生成分析");
        body.put("action", "ANALYZE_REGION");
        body.put("planExecution", singletonMap("status", "MATCHED"));

        MapAgentRunResponse response = new ObjectMapper().convertValue(body, MapAgentRunResponse.class);

        assertEquals("已生成分析", response.getAnswer());
        assertEquals("MATCHED", response.getPlanExecution().get("status"));
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
