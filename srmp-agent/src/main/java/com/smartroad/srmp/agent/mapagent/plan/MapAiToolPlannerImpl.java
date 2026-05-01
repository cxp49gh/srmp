package com.smartroad.srmp.agent.mapagent.plan;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.mapagent.dto.MapAiIntent;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Phase37.5：对象类型驱动的工具规划器。
 *
 * 将 MapAiAgentServiceImpl 中不断增长的工具规划逻辑收口到独立组件。
 */
@Component
public class MapAiToolPlannerImpl implements MapAiToolPlanner {

    @Override
    public List<String> plan(MapAiIntent intent, MapAiContext context, Map<String, Object> options, String message) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();

        if (intent == MapAiIntent.NEARBY_ANALYSIS) {
            tools.add("gis.queryNearbyObjects");
        }
        if (intent == MapAiIntent.REGION_ANALYSIS) {
            tools.add("gis.queryRegionSummary");
        }

        if (intent == MapAiIntent.OBJECT_ANALYSIS || intent == MapAiIntent.SOLUTION_GENERATE) {
            planObjectTools(tools, context);
        }

        if (intent == MapAiIntent.TEMPLATE_VERIFY) {
            tools.add("template.match");
        }

        if (intent == MapAiIntent.SOLUTION_GENERATE && isExplicitSolutionDraftRequest(message)) {
            tools.add("solution.generateDraft");
        }

        if (useKnowledge(options) || intent == MapAiIntent.KNOWLEDGE_QA
                || intent == MapAiIntent.SOLUTION_GENERATE
                || intent == MapAiIntent.OBJECT_ANALYSIS
                || intent == MapAiIntent.REGION_ANALYSIS) {
            tools.add("knowledge.retrieve");
        }

        if (tools.isEmpty()) {
            tools.add("knowledge.retrieve");
        }
        return new ArrayList<>(tools);
    }

    private void planObjectTools(LinkedHashSet<String> tools, MapAiContext context) {
        Map<String, Object> mapObject = context == null ? null : context.getMapObject();
        String type = firstString(mapObject, "objectType", "object_type", "type", "layerType");

        if (equalsAny(type, "ROAD_ROUTE", "ROUTE")) {
            tools.add("gis.queryAssessmentResults");
            tools.add("gis.queryDiseases");
            return;
        }

        if (equalsAny(type, "ROAD_SECTION", "SECTION", "ROAD_SEGMENT", "SEGMENT")) {
            tools.add("gis.queryAssessmentResults");
            tools.add("gis.queryDiseases");
            tools.add("gis.queryDiseasesByStakeRange");
            return;
        }

        if (equalsAny(type, "DISEASE", "DISEASE_RECORD")) {
            tools.add("gis.queryNearbyObjects");
            return;
        }

        if (equalsAny(type, "ASSESSMENT_RESULT", "ASSESSMENT", "EVALUATION_UNIT")) {
            tools.add("gis.queryAssessmentResults");
            tools.add("gis.queryDiseasesByStakeRange");
        }
    }

    private boolean useKnowledge(Map<String, Object> options) {
        if (options == null || !options.containsKey("useKnowledge")) {
            return true;
        }
        return Boolean.parseBoolean(String.valueOf(options.get("useKnowledge")));
    }

    private boolean isExplicitSolutionDraftRequest(String message) {
        String text = message == null ? "" : message;
        return containsAny(text,
                "生成方案", "方案草稿", "生成草稿", "保存方案", "保存为方案", "保存为方案任务",
                "形成方案", "生成任务", "生成养护方案", "保存任务", "创建方案任务");
    }

    private boolean equalsAny(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String c : candidates) {
            if (value.equalsIgnoreCase(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (word != null && value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value).trim();
            }
        }
        Object raw = map.get("raw");
        if (raw instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) raw;
            for (String key : keys) {
                Object value = rawMap.get(key);
                if (value != null && String.valueOf(value).trim().length() > 0) {
                    return String.valueOf(value).trim();
                }
            }
        }
        return null;
    }
}
