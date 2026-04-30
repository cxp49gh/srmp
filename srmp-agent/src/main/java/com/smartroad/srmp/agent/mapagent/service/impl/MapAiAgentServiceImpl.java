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
                AiTraceContext.StepTimer toolTimer = trace.step("tool_execute", "工具执行 - " + toolName);
                AiToolResult result = tool.execute(toolContext, defaultArgs(toolName, message));
                toolResults.add(result);
                Map<String, Object> stepData = new LinkedHashMap<>();
                stepData.put("toolName", toolName);
                stepData.put("summary", result.getSummary());
                stepData.put("success", result.isSuccess());
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
            cleanTimer.success();

            response.setAnswer(answer);
            response.setIntent(intent.name());
            response.setMapContext(context);
            response.setToolResults(toolResults);
            response.setKnowledgeSources(knowledgeSources);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sources", knowledgeSources);
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
        sb.append("建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核，并根据严重程度、指标分值和周边关联对象确定处置优先级。\n");
        return sb.toString();
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
