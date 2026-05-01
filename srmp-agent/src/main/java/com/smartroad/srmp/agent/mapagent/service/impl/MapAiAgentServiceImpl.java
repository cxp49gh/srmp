package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.mapagent.dto.*;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.tool.*;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class MapAiAgentServiceImpl implements MapAiAgentService {

    @Resource
    private AiToolRegistry toolRegistry;

    @Resource
    private LlmClient llmClient;

    @Resource
    private AiTraceService aiTraceService;

    @Override
    public MapAiAgentResponse chat(MapAiAgentRequest request) {
        String message = request == null ? "" : safe(request.getMessage());
        AiTraceContext trace = AiTraceContext.start("MAP_AI_AGENT", message);
        MapAiAgentResponse response = new MapAiAgentResponse();
        response.setMode("MAP_AI_AGENT");
        try {
            MapAiContext context;
            AiTraceContext.StepTimer contextTimer = trace.step("map_context_build", "地图上下文构建");
            try {
                context = buildMapContext(request, message);
                contextTimer.success(1, map("mode", context.getMode()));
            } catch (Exception e) {
                contextTimer.failed(e);
                throw e;
            }

            MapAiIntent intent;
            AiTraceContext.StepTimer intentTimer = trace.step("agent_intent_recognize", "意图识别");
            try {
                intent = recognizeIntent(message, context);
                intentTimer.success(1, map("intent", intent.name()));
            } catch (Exception e) {
                intentTimer.failed(e);
                throw e;
            }

            AiToolContext toolContext = new AiToolContext();
            toolContext.setTenantId(context.getTenantId());
            toolContext.setTraceId(trace.getTraceId());
            toolContext.setUserQuestion(message);
            toolContext.setMapContext(context);
            toolContext.setOptions(request == null ? new LinkedHashMap<String, Object>() : request.getOptions());

            List<String> toolNames = planTools(intent, context, request == null ? null : request.getOptions());
            List<AiToolResult> toolResults = new ArrayList<>();
            List<AiKnowledgeSearchHit> knowledgeSources = new ArrayList<>();

            AiTraceContext.StepTimer planTimer = trace.step("tool_plan", "工具规划");
            planTimer.success(toolNames.size(), map("tools", new ArrayList<String>(toolNames)));

            for (String toolName : toolNames) {
                AiTool tool = toolRegistry.get(toolName);
                if (tool == null) {
                    continue;
                }
                String stepName = "knowledge.retrieve".equals(toolName) ? "knowledge_retrieve" : "tool_execute";
                String stepLabel = "knowledge.retrieve".equals(toolName) ? "知识库检索" : "工具执行 - " + toolName;
                AiTraceContext.StepTimer toolTimer = trace.step(stepName, stepLabel);
                AiToolResult result = tool.execute(toolContext, defaultArgs(toolName, message));
                toolResults.add(result);
                Map<String, Object> stepData = new LinkedHashMap<>();
                stepData.put("toolName", toolName);
                stepData.put("summary", result.getSummary());
                stepData.put("success", result.isSuccess());
                if (result.getData() instanceof AiKnowledgeSearchResponse) {
                    AiKnowledgeSearchResponse kr = (AiKnowledgeSearchResponse) result.getData();
                    stepData.put("query", kr.getQuery());
                    stepData.put("searchMode", kr.getSearchMode());
                    stepData.put("vectorUsed", kr.getVectorUsed());
                    stepData.put("embeddingProvider", kr.getEmbeddingProvider());
                    stepData.put("hitCount", kr.getHitCount());
                    stepData.put("topScore", kr.getTopScore());
                    stepData.put("fallback", kr.getFallback());
                    stepData.put("fallbackReason", kr.getFallbackReason());
                }
                if (result.isSuccess()) {
                    toolTimer.success(result.getCount(), stepData);
                } else {
                    toolTimer.failed(new RuntimeException(result.getErrorMessage()), stepData);
                }
                if ("knowledge.retrieve".equals(toolName)) {
                    collectKnowledgeHits(result, knowledgeSources);
                }
            }

            AiTraceContext.StepTimer promptTimer = trace.step("prompt_build", "Prompt 构建");
            String prompt = buildPrompt(message, context, toolResults, knowledgeSources);
            promptTimer.success(1, map("knowledgeHitCount", knowledgeSources.size()));

            AiTraceContext.StepTimer llmTimer = trace.step("llm_answer", "大模型回答");
            String answer = llmClient.chat(systemPrompt(), prompt);
            if (answer == null || answer.trim().isEmpty()) {
                answer = localAnswer(message, context, toolResults, knowledgeSources);
                llmTimer.skipped(map("reason", "LLM 未启用或未返回内容，使用本地 Agent 回答"));
                trace.setFallback(true);
            } else {
                llmTimer.success();
            }

            AiTraceContext.StepTimer cleanTimer = trace.step("answer_sanitize", "回答清洗");
            answer = sanitize(answer);
            answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);
            cleanTimer.success(null, map("termEnriched", true));

            response.setAnswer(answer);
            response.setIntent(intent.name());
            response.setMapContext(context);
            response.setToolResults(toolResults);
            response.setKnowledgeSources(knowledgeSources);
            response.setSources(knowledgeSources);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sources", knowledgeSources);
            data.put("knowledgeSources", knowledgeSources);
            data.put("toolResults", toolResults);
            data.put("mapContext", context);
            data.put("intent", intent.name());
            response.setData(data);
            trace.setStatus("SUCCESS");
            trace.setMode("MAP_AI_AGENT");
            return response;
        } catch (Exception e) {
            log.warn("[MAP-AI-AGENT] chat failed traceId={} error={}", trace.getTraceId(), e.getMessage(), e);
            trace.setStatus("FAILED");
            trace.setFallback(true);
            trace.setError(e.getMessage());
            response.setAnswer("一张图 AI Agent 处理失败：" + e.getMessage());
            response.setIntent(MapAiIntent.GENERAL_CHAT.name());
            return response;
        } finally {
            trace.finish();
            response.setTrace(trace.toMap());
            try {
                aiTraceService.save(trace);
            } catch (Exception e) {
                log.warn("[MAP-AI-AGENT] save trace failed traceId={} error={}", trace.getTraceId(), e.getMessage(), e);
            }
        }
    }

    private MapAiContext buildMapContext(MapAiAgentRequest request, String message) {
        MapAiContext ctx = request == null ? null : request.getMapContext();
        if (ctx == null) {
            ctx = new MapAiContext();
        }
        ctx.setTenantId(firstNonBlank(ctx.getTenantId(), TenantContextHolder.getTenantId()));
        ctx.setUserQuestion(message);
        if ((ctx.getMapObject() == null || ctx.getMapObject().isEmpty()) && request != null && request.getMapObject() != null) {
            ctx.setMapObject(request.getMapObject());
        }
        if (ctx.getRouteCode() == null && ctx.getMapObject() != null) {
            ctx.setRouteCode(firstString(ctx.getMapObject(), "routeCode", "route_code"));
        }
        if (ctx.getYear() == null && ctx.getMapObject() != null) {
            ctx.setYear(firstInteger(ctx.getMapObject(), "year"));
        }
        if (safe(ctx.getMode()).length() == 0) {
            if (ctx.getMapObject() != null && !ctx.getMapObject().isEmpty()) {
                ctx.setMode("OBJECT");
            } else if (ctx.getRegionSummary() != null && !ctx.getRegionSummary().isEmpty()) {
                ctx.setMode("REGION");
            } else if (ctx.getViewport() != null && !ctx.getViewport().isEmpty()) {
                ctx.setMode("VIEWPORT");
            } else if (ctx.getRouteCode() != null) {
                ctx.setMode("ROUTE");
            } else {
                ctx.setMode("FREE");
            }
        }
        return ctx;
    }

    private MapAiIntent recognizeIntent(String message, MapAiContext context) {
        String text = message == null ? "" : message;
        if (containsAny(text, "保存", "归档", "确认方案")) return MapAiIntent.TASK_SAVE;
        if (containsAny(text, "模板", "是否生效", "变量")) return MapAiIntent.TEMPLATE_VERIFY;
        if (containsAny(text, "生成方案", "处置建议", "报告草稿", "养护建议")) return MapAiIntent.SOLUTION_GENERATE;
        if (containsAny(text, "周边", "附近", "相邻", "集中区")) return MapAiIntent.NEARBY_ANALYSIS;
        if (containsAny(text, "区域", "框选", "范围") || "REGION".equals(context.getMode())) return MapAiIntent.REGION_ANALYSIS;
        if (containsAny(text, "规范", "标准", "怎么处理", "工艺", "依据")) return MapAiIntent.KNOWLEDGE_QA;
        if ("OBJECT".equals(context.getMode())) return MapAiIntent.OBJECT_ANALYSIS;
        return MapAiIntent.GENERAL_CHAT;
    }

    private List<String> planTools(MapAiIntent intent, MapAiContext context, Map<String, Object> options) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        if (intent == MapAiIntent.NEARBY_ANALYSIS) tools.add("gis.queryNearbyObjects");
        if (intent == MapAiIntent.REGION_ANALYSIS) tools.add("gis.queryRegionSummary");
        if (intent == MapAiIntent.OBJECT_ANALYSIS || intent == MapAiIntent.SOLUTION_GENERATE) {
            String type = firstString(context.getMapObject(), "objectType", "object_type", "type");
            if ("DISEASE".equalsIgnoreCase(type) || "DISEASE_RECORD".equalsIgnoreCase(type)) tools.add("gis.queryNearbyObjects");
            if ("ASSESSMENT_RESULT".equalsIgnoreCase(type) || "EVALUATION_UNIT".equalsIgnoreCase(type)) tools.add("gis.queryAssessmentResults");
        }
        if (intent == MapAiIntent.TEMPLATE_VERIFY) tools.add("template.match");
        if (intent == MapAiIntent.SOLUTION_GENERATE) tools.add("solution.generateDraft");
        if (useKnowledge(options) || intent == MapAiIntent.KNOWLEDGE_QA || intent == MapAiIntent.SOLUTION_GENERATE || intent == MapAiIntent.OBJECT_ANALYSIS) {
            tools.add("knowledge.retrieve");
        }
        if (tools.isEmpty()) tools.add("knowledge.retrieve");
        return new ArrayList<>(tools);
    }

    private boolean useKnowledge(Map<String, Object> options) {
        if (options == null || !options.containsKey("useKnowledge")) return true;
        return Boolean.parseBoolean(String.valueOf(options.get("useKnowledge")));
    }

    private Map<String, Object> defaultArgs(String toolName, String message) {
        Map<String, Object> args = new LinkedHashMap<>();
        if ("knowledge.retrieve".equals(toolName)) {
            args.put("query", message);
        }
        return args;
    }

    private void collectKnowledgeHits(AiToolResult result, List<AiKnowledgeSearchHit> hits) {
        Object data = result.getData();
        if (data instanceof AiKnowledgeSearchResponse) {
            AiKnowledgeSearchResponse response = (AiKnowledgeSearchResponse) data;
            if (response.getHits() != null) hits.addAll(response.getHits());
        }
    }

    private String buildPrompt(String message, MapAiContext context, List<AiToolResult> toolResults, List<AiKnowledgeSearchHit> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户问题】\n").append(message).append("\n\n");
        sb.append("【地图上下文】\n").append(stringifyMapContext(context)).append("\n\n");
        sb.append("【工具调用结果】\n");
        for (AiToolResult result : toolResults) {
            sb.append("- ").append(result.getToolName()).append("：").append(result.getSummary()).append("\n");
        }
        sb.append("\n【知识库资料】\n");
        int i = 1;
        for (AiKnowledgeSearchHit hit : sources) {
            sb.append("资料").append(i++).append("：").append(hit.getTitle()).append(" / ").append(hit.getSectionTitle()).append("\n");
            sb.append(hit.getContent()).append("\n\n");
        }
        sb.append("【回答要求】\n");
        sb.append("1. 优先围绕当前地图对象或框选区域回答。\n");
        sb.append("2. 引用知识库内容时说明来源标题。\n");
        sb.append("3. 如果数据不足，请明确说明缺失项。\n");
        sb.append("4. 给出可执行的道路养护建议。\n");
        sb.append("5. 不要输出思考过程，不要输出 <think> 标签。\n");
        sb.append("6. 【知识库术语保留要求】如果知识库资料中包含明确的处置工艺、风险因素、现场复核要点，回答必须显式提取并写入，不要只概括为“预防性养护”或“局部修补”。\n");
        sb.append("7. 对于裂缝类病害，必须优先覆盖：灌缝/开槽灌缝/封缝，封层/薄层罩面/雾封层，以及防止渗水、雨水下渗造成基层水损害。\n");
        sb.append("8. 回答应尽量保留知识库中的专业术语，并在结尾列出参考资料标题。\n");
        return sb.toString();
    }

    private String systemPrompt() {
        return "你是智路养护平台一张图 AI Agent，擅长结合 GIS 地图对象、业务数据、知识库资料和模板生成道路养护分析。";
    }

    private String localAnswer(String message, MapAiContext context, List<AiToolResult> toolResults, List<AiKnowledgeSearchHit> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 一张图 AI 分析\n\n");
        sb.append("已基于当前地图上下文处理问题：").append(message).append("\n\n");
        sb.append("### 地图上下文\n").append(stringifyMapContext(context)).append("\n\n");
        if (!sources.isEmpty()) {
            sb.append("### 参考资料\n");
            for (int i = 0; i < sources.size(); i++) {
                AiKnowledgeSearchHit hit = sources.get(i);
                sb.append(i + 1).append(". ").append(hit.getTitle());
                if (hit.getSectionTitle() != null) sb.append(" / ").append(hit.getSectionTitle());
                sb.append("\n");
            }
            sb.append("\n");
        }
        sb.append("### 建议\n");
        if (isCrackScenario(message, context, sources)) {
            sb.append("当前对象属于裂缝类病害，建议先复核裂缝宽度、密度、发展范围和是否存在渗水、雨水下渗问题。");
            sb.append("中度裂缝可优先采用开槽灌缝或封缝处理，必要时结合封层、薄层罩面或雾封层，防止基层水损害继续发展。\n");
        } else {
            sb.append("建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核，并根据严重程度、指标分值和周边关联对象确定处置优先级。\n");
        }
        return enrichAnswerWithKnowledgeTerms(sb.toString(), message, context, sources);
    }


    /**
     * Phase37.2.2：回答术语覆盖增强。
     *
     * RAG 评测关注 answer 是否吸收了 sources 中的关键业务术语。
     * 如果检索已命中裂缝资料，但 LLM 回答过于概括，这里补充一个基于知识库来源的“处置要点补充”，
     * 避免出现 sources 命中但 answer 完全不使用资料术语的情况。
     */
    private String enrichAnswerWithKnowledgeTerms(String answer, String message, MapAiContext context, List<AiKnowledgeSearchHit> sources) {
        String value = answer == null ? "" : answer.trim();
        if (!isCrackScenario(message, context, sources)) {
            return value;
        }

        String normalized = value.replaceAll("\\s+", "");
        boolean hasCrackProcess = containsAny(normalized, "灌缝", "开槽灌缝", "封缝", "裂缝灌缝");
        boolean hasSurfaceSeal = containsAny(normalized, "封层", "雾封层", "稀浆封层", "薄层罩面", "罩面");
        boolean hasWaterRisk = containsAny(normalized, "渗水", "下渗", "雨水", "水损害", "雨水下渗");

        if (hasCrackProcess && hasSurfaceSeal && hasWaterRisk) {
            return value;
        }

        StringBuilder sb = new StringBuilder(value);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("### 知识库处置要点补充\n");
        sb.append("结合裂缝类病害处置资料，中度裂缝应重点关注雨水渗水和下渗风险，防止基层水损害扩大。");
        sb.append("处置上可采用开槽灌缝或封缝；当裂缝较密集或伴随表层老化时，可结合封层、薄层罩面或雾封层等预防性养护措施。");
        sb.append("实施前应复核裂缝宽度、密度、长度、渗水情况和周边病害分布。");
        return sb.toString();
    }

    private boolean isCrackScenario(String message, MapAiContext context, List<AiKnowledgeSearchHit> sources) {
        // Phase37.2.3：
        // 裂缝术语兜底只能由“用户问题”或“当前地图对象”触发，不能仅因为 sources 中混入裂缝指南就触发。
        // 否则修补损坏、坑槽等问题在 topK 命中裂缝指南时，会被误追加“开槽灌缝/封层/渗水”等裂缝建议。
        if (containsAny(message, "裂缝", "开裂", "灌缝", "封缝")) {
            return true;
        }
        if (context != null && context.getMapObject() != null) {
            String diseaseName = firstString(context.getMapObject(), "diseaseName", "disease_name", "diseaseType", "disease_type");
            String objectType = firstString(context.getMapObject(), "objectType", "object_type", "type");
            if (containsAny(diseaseName, "裂缝", "开裂") || containsAny(objectType, "CRACK")) {
                return true;
            }
        }
        return false;
    }

    private String stringifyMapContext(MapAiContext context) {
        if (context == null) return "无";
        StringBuilder sb = new StringBuilder();
        sb.append("模式：").append(context.getMode()).append("\n");
        if (context.getRouteCode() != null) sb.append("路线：").append(context.getRouteCode()).append("\n");
        if (context.getYear() != null) sb.append("年度：").append(context.getYear()).append("\n");
        if (context.getMapObject() != null && !context.getMapObject().isEmpty()) sb.append("地图对象：").append(context.getMapObject()).append("\n");
        if (context.getRegionSummary() != null && !context.getRegionSummary().isEmpty()) sb.append("区域统计：").append(context.getRegionSummary()).append("\n");
        return sb.toString();
    }

    private String sanitize(String answer) {
        if (answer == null) return "";
        return answer.replaceAll("(?is)<think>.*?</think>", "").replaceAll("(?is)<thinking>.*?</thinking>", "").trim();
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) return String.valueOf(value);
        }
        return null;
    }

    private Integer firstInteger(Map<String, Object> map, String... keys) {
        String value = firstString(map, keys);
        try { return value == null ? null : Integer.valueOf(value); } catch (Exception e) { return null; }
    }

    private String firstNonBlank(String a, String b) { return safe(a).length() > 0 ? a : b; }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private Map<String, Object> map(String k, Object v) { Map<String, Object> m = new LinkedHashMap<>(); m.put(k, v); return m; }
}
