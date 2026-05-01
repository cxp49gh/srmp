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
            if (r.getTopScore() != null) {
                scoreSum += r.getTopScore();
                scoreCount++;
            }
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
            result.setAnswerPreview(preview(agentResponse.getAnswer(), 600));

            collectSources(agentResponse, result);
            collectTools(agentResponse, result);
            checkKeywords(c, result);
            checkKeywordGroups(c, result);
            checkSources(c, result);
            checkBasicRag(req, result);

            if (result.getFailReasons().contains("KEYWORD_GROUP_HIT_RATIO_LOW")
                    && Boolean.TRUE.equals(result.getSourceMatched())
                    && Boolean.TRUE.equals(result.getVectorUsed())) {
                addFail(
                        result,
                        "ANSWER_DID_NOT_USE_RETRIEVED_KNOWLEDGE",
                        "检索命中预期来源且 vectorUsed=true，但回答未覆盖检索资料中的关键处置术语"
                );
            }

            result.setPassed(result.getFailReasons().isEmpty());
        } catch (Exception e) {
            addFail(result, "EVAL_EXCEPTION", e.getMessage());
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
                String title = str(m.get("title"));
                if (title != null && !title.trim().isEmpty()) {
                    result.getActualSourceTitles().add(title);
                }
                Object score = m.get("score");
                if (result.getTopScore() == null && score instanceof Number) {
                    result.setTopScore(((Number) score).doubleValue());
                }
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

        if (keywords.isEmpty()) {
            result.setKeywordHitRatio(1.0d);
            return;
        }

        String answer = normalize(result.getAnswer());
        for (String kw : keywords) {
            if (kw == null || kw.trim().isEmpty()) continue;
            if (answer.contains(normalize(kw))) {
                result.setKeywordMatchedCount(result.getKeywordMatchedCount() + 1);
                result.getMatchedKeywords().add(kw);
            } else {
                result.getMissingKeywords().add(kw);
            }
        }

        double ratio = result.getKeywordTotal() == 0 ? 1.0d : result.getKeywordMatchedCount() * 1.0d / result.getKeywordTotal();
        result.setKeywordHitRatio(ratio);

        double threshold = c.getMinKeywordHitRatio() == null ? 1.0d : c.getMinKeywordHitRatio();
        if (ratio + 1e-12 < threshold) {
            addFail(
                    result,
                    "KEYWORD_HIT_RATIO_LOW",
                    "keywordHitRatio=" + ratio + ", threshold=" + threshold + ", missing=" + result.getMissingKeywords()
            );
        }
    }

    private void checkKeywordGroups(RagEvalCase c, RagEvalCaseResult result) {
        List<List<String>> groups = c.getExpectedKeywordGroups() == null ? Collections.emptyList() : c.getExpectedKeywordGroups();
        result.setKeywordGroupTotal(groups.size());

        if (groups.isEmpty()) {
            result.setKeywordGroupHitRatio(1.0d);
            return;
        }

        String answer = normalize(result.getAnswer());

        for (List<String> group : groups) {
            boolean hit = false;
            if (group != null) {
                for (String kw : group) {
                    if (kw != null && !kw.trim().isEmpty() && answer.contains(normalize(kw))) {
                        hit = true;
                        break;
                    }
                }
            }

            if (hit) {
                result.setKeywordGroupMatchedCount(result.getKeywordGroupMatchedCount() + 1);
                result.getMatchedKeywordGroups().add(group);
            } else {
                result.getMissingKeywordGroups().add(group);
            }
        }

        double ratio = result.getKeywordGroupTotal() == 0 ? 1.0d : result.getKeywordGroupMatchedCount() * 1.0d / result.getKeywordGroupTotal();
        result.setKeywordGroupHitRatio(ratio);

        double threshold = c.getMinKeywordGroupHitRatio() == null ? 1.0d : c.getMinKeywordGroupHitRatio();
        if (ratio + 1e-12 < threshold) {
            addFail(
                    result,
                    "KEYWORD_GROUP_HIT_RATIO_LOW",
                    "keywordGroupHitRatio=" + ratio + ", threshold=" + threshold + ", missingGroups=" + result.getMissingKeywordGroups()
            );
        }
    }

    private void checkSources(RagEvalCase c, RagEvalCaseResult result) {
        List<String> expected = c.getExpectedSources() == null ? Collections.emptyList() : c.getExpectedSources();
        result.setExpectedSources(new ArrayList<>(expected));

        if (expected.isEmpty()) {
            result.setSourceMatched(true);
            return;
        }

        for (Map<String, Object> s : result.getSources()) {
            String title = normalize(str(s.get("title")));
            for (String e : expected) {
                if (title != null && title.contains(normalize(e))) {
                    result.setSourceMatched(true);
                    return;
                }
            }
        }

        addFail(result, "SOURCE_NOT_MATCHED", "expected=" + expected + ", actual=" + result.getActualSourceTitles());
    }

    private void checkBasicRag(RagEvalRequest req, RagEvalCaseResult result) {
        if (!Boolean.TRUE.equals(result.getKnowledgeToolCalled())) {
            addFail(result, "TOOL_NOT_CALLED", "knowledge.retrieve 未被调用");
        }

        if (result.getSourceCount() <= 0) {
            addFail(result, "SOURCE_EMPTY", "sources 为空");
        }

        if (Boolean.TRUE.equals(req == null ? null : req.getRequireVectorUsed()) && !Boolean.TRUE.equals(result.getVectorUsed())) {
            addFail(result, "VECTOR_NOT_USED", "vectorUsed=false");
        }
    }

    private void addFail(RagEvalCaseResult result, String code, String detail) {
        if (!result.getFailReasons().contains(code)) {
            result.getFailReasons().add(code);
        }
        String msg = detail == null || detail.trim().isEmpty() ? code : code + ": " + detail;
        result.getErrors().add(msg);
    }

    private Map<String, Object> beanToMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null) return result;

        if (value instanceof Map) {
            result.putAll((Map<String, Object>) value);
            return result;
        }

        try {
            for (java.lang.reflect.Method m : value.getClass().getMethods()) {
                if (!m.getName().startsWith("get") || m.getParameterCount() != 0 || "getClass".equals(m.getName())) continue;
                String name = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                result.put(name, m.invoke(value));
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private String normalize(Object v) {
        if (v == null) return "";
        return String.valueOf(v)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("，", ",")
                .replace("。", ".")
                .replace("（", "(")
                .replace("）", ")");
    }

    private String preview(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Integer intValue(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 2026;
    }
}
