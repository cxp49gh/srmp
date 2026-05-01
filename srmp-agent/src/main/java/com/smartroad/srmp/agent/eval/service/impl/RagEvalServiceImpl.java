package com.smartroad.srmp.agent.eval.service.impl;

import com.smartroad.srmp.agent.eval.dto.RagEvalCase;
import com.smartroad.srmp.agent.eval.dto.RagEvalRequest;
import com.smartroad.srmp.agent.eval.service.RagEvalService;
import com.smartroad.srmp.agent.eval.vo.RagEvalCaseResult;
import com.smartroad.srmp.agent.eval.vo.RagEvalResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class RagEvalServiceImpl implements RagEvalService {

    @Resource
    private MapAiAgentService mapAiAgentService;

    @Override
    public RagEvalResponse run(RagEvalRequest request) {
        RagEvalResponse response = new RagEvalResponse();
        List<RagEvalCase> cases = request == null || request.getCases() == null ? Collections.emptyList() : request.getCases();
        double scoreSum = 0.0d;
        int scoreCount = 0;
        for (RagEvalCase c : cases) {
            RagEvalCaseResult r = runOne(request, c);
            response.getResults().add(r);
            if (Boolean.TRUE.equals(r.getPassed())) response.setPassed(response.getPassed() + 1);
            if (Boolean.TRUE.equals(r.getKnowledgeToolCalled())) response.setKnowledgeToolCalledCount(response.getKnowledgeToolCalledCount() + 1);
            if (Boolean.TRUE.equals(r.getVectorUsed())) response.setVectorUsedCount(response.getVectorUsedCount() + 1);
            if (r.getTopScore() != null) { scoreSum += r.getTopScore(); scoreCount++; }
        }
        response.setTotal(response.getResults().size());
        response.setPassRate(response.getTotal() == 0 ? 0.0d : response.getPassed() * 1.0d / response.getTotal());
        response.setAvgTopScore(scoreCount == 0 ? null : scoreSum / scoreCount);
        return response;
    }

    private RagEvalCaseResult runOne(RagEvalRequest req, RagEvalCase c) {
        RagEvalCaseResult result = new RagEvalCaseResult();
        result.setId(c.getId());
        result.setQuestion(c.getQuestion());
        try {
            MapAiAgentRequest agentRequest = new MapAiAgentRequest();
            agentRequest.setMessage(c.getQuestion());
            agentRequest.setOptions(mergeOptions(req, c));
            agentRequest.setMapContext(toMapAiContext(req, c));
            MapAiAgentResponse agentResponse = mapAiAgentService.chat(agentRequest);
            result.setAnswer(agentResponse.getAnswer());
            collectSources(agentResponse, result);
            collectTools(agentResponse, result);
            checkKeywords(c, result);
            checkSources(c, result);
            if (Boolean.TRUE.equals(req.getRequireVectorUsed()) && !Boolean.TRUE.equals(result.getVectorUsed())) {
                result.getErrors().add("vectorUsed=false");
            }
            boolean passed = result.getErrors().isEmpty()
                    && Boolean.TRUE.equals(result.getKnowledgeToolCalled())
                    && result.getSourceCount() > 0
                    && result.getMissingKeywords().isEmpty();
            if (Boolean.TRUE.equals(req.getRequireVectorUsed())) passed = passed && Boolean.TRUE.equals(result.getVectorUsed());
            result.setPassed(passed);
        } catch (Exception e) {
            result.getErrors().add(e.getMessage());
            result.setPassed(false);
        }
        return result;
    }

    private Map<String, Object> mergeOptions(RagEvalRequest req, RagEvalCase c) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("useKnowledge", true);
        options.put("useBusinessData", true);
        options.put("useTools", true);
        options.put("topK", req == null || req.getTopK() == null ? 5 : req.getTopK());
        if (c.getOptions() != null) options.putAll(c.getOptions());
        return options;
    }

    private MapAiContext toMapAiContext(RagEvalRequest req, RagEvalCase c) {
        MapAiContext ctx = new MapAiContext();
        Map<String, Object> m = c.getMapContext() == null ? Collections.emptyMap() : c.getMapContext();
        ctx.setTenantId(req == null ? null : req.getTenantId());
        ctx.setMode(str(m.getOrDefault("mode", "OBJECT")));
        ctx.setRouteCode(str(m.get("routeCode")));
        ctx.setYear(intValue(m.getOrDefault("year", 2026)));
        Object mapObject = m.get("mapObject");
        if (mapObject instanceof Map) ctx.setMapObject((Map<String, Object>) mapObject);
        Object regionSummary = m.get("regionSummary");
        if (regionSummary instanceof Map) ctx.setRegionSummary((Map<String, Object>) regionSummary);
        return ctx;
    }

    private void collectSources(MapAiAgentResponse resp, RagEvalCaseResult result) {
        if (resp.getSources() != null) {
            for (Object s : resp.getSources()) {
                Map<String, Object> m = beanToMap(s);
                result.getSources().add(m);
                Object score = m.get("score");
                if (result.getTopScore() == null && score instanceof Number) result.setTopScore(((Number) score).doubleValue());
            }
        }
        result.setSourceCount(result.getSources().size());
    }

    private void collectTools(MapAiAgentResponse resp, RagEvalCaseResult result) {
        if (resp.getToolResults() == null) return;
        for (AiToolResult tool : resp.getToolResults()) {
            if ("knowledge.retrieve".equals(tool.getToolName())) {
                result.setKnowledgeToolCalled(true);
                Map<String, Object> data = beanToMap(tool.getData());
                Object vectorUsed = data.get("vectorUsed");
                if (vectorUsed instanceof Boolean) result.setVectorUsed((Boolean) vectorUsed);
                Object searchMode = data.get("searchMode");
                if (searchMode != null) result.setSearchMode(String.valueOf(searchMode));
            }
        }
    }

    private void checkKeywords(RagEvalCase c, RagEvalCaseResult result) {
        List<String> keywords = c.getExpectedKeywords() == null ? Collections.emptyList() : c.getExpectedKeywords();
        result.setKeywordTotal(keywords.size());
        String answer = result.getAnswer() == null ? "" : result.getAnswer();
        for (String kw : keywords) {
            if (kw == null || kw.trim().isEmpty()) continue;
            if (answer.contains(kw)) result.setKeywordMatchedCount(result.getKeywordMatchedCount() + 1);
            else result.getMissingKeywords().add(kw);
        }
        if (!result.getMissingKeywords().isEmpty()) result.getErrors().add("missingKeywords=" + result.getMissingKeywords());
    }

    private void checkSources(RagEvalCase c, RagEvalCaseResult result) {
        List<String> expected = c.getExpectedSources() == null ? Collections.emptyList() : c.getExpectedSources();
        if (expected.isEmpty()) { result.setSourceMatched(true); return; }
        for (Map<String, Object> s : result.getSources()) {
            String title = str(s.get("title"));
            for (String e : expected) {
                if (title != null && title.contains(e)) { result.setSourceMatched(true); return; }
            }
        }
        result.getErrors().add("expected source not matched: " + expected);
    }

    private Map<String, Object> beanToMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null) return result;
        if (value instanceof Map) { result.putAll((Map<String, Object>) value); return result; }
        try {
            for (java.lang.reflect.Method m : value.getClass().getMethods()) {
                if (!m.getName().startsWith("get") || m.getParameterCount() != 0 || "getClass".equals(m.getName())) continue;
                String name = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                result.put(name, m.invoke(value));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String str(Object v) { return v == null ? null : String.valueOf(v); }
    private Integer intValue(Object v) { return v instanceof Number ? ((Number) v).intValue() : 2026; }
}
