package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentMapQueryRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.service.AgentAnalysisService;
import com.smartroad.srmp.agent.service.AgentDataQueryService;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.agent.vo.AgentAnalysisResponse;
import com.smartroad.srmp.agent.vo.AgentMapQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Service
public class AgentAnalysisServiceImpl implements AgentAnalysisService {

    @Resource
    private AgentDataQueryService dataQueryService;

    @Resource
    private LlmClient llmClient;

    @Resource
    private AiTraceService aiTraceService;

    @Override
    public AgentAnalysisResponse analyzeRoute(AgentAnalysisRequest request) {
        return traced("ROUTE_ANALYSIS", traceMessage("路线分析", request), () -> analyzeRouteInternal(request));
    }

    private AgentAnalysisResponse analyzeRouteInternal(AgentAnalysisRequest request) {
        Map<String, Object> route = dataQueryService.routeSummary(request);
        Map<String, Object> disease = dataQueryService.diseaseSummary(request);
        Map<String, Object> assessment = dataQueryService.assessmentSummary(request);
        List<Map<String, Object>> poor = dataQueryService.poorAssessmentResults(request, 10);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("routeSummary", route);
        data.put("diseaseSummary", disease);
        data.put("assessmentSummary", assessment);
        data.put("poorAssessmentTop10", poor);

        String fallback = buildRouteSummary(request, route, disease, assessment, poor);
        String llm = callLlm("你是公路养护专家，请基于结构化数据生成专业、简洁、可执行的路线综合分析。", fallback, data);

        return response("路线综合分析", llm, fallback, data);
    }

    @Override
    public AgentAnalysisResponse analyzeDisease(AgentAnalysisRequest request) {
        return traced("DISEASE_ANALYSIS", traceMessage("病害分析", request), () -> analyzeDiseaseInternal(request));
    }

    private AgentAnalysisResponse analyzeDiseaseInternal(AgentAnalysisRequest request) {
        Map<String, Object> disease = dataQueryService.diseaseSummary(request);
        List<Map<String, Object>> top = dataQueryService.topDiseaseUnits(request, 10);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("diseaseSummary", disease);
        data.put("topDiseaseUnits", top);

        String fallback = buildDiseaseSummary(request, disease, top);
        String llm = callLlm("你是道路病害分析专家，请识别病害热点、严重程度和优先处置建议。", fallback, data);

        return response("病害热点分析", llm, fallback, data);
    }

    @Override
    public AgentAnalysisResponse analyzeAssessment(AgentAnalysisRequest request) {
        return traced("ASSESSMENT_ANALYSIS", traceMessage("评定分析", request), () -> analyzeAssessmentInternal(request));
    }

    private AgentAnalysisResponse analyzeAssessmentInternal(AgentAnalysisRequest request) {
        Map<String, Object> assessment = dataQueryService.assessmentSummary(request);
        List<Map<String, Object>> poor = dataQueryService.poorAssessmentResults(request, 10);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assessmentSummary", assessment);
        data.put("poorAssessmentTop10", poor);

        String fallback = buildAssessmentSummary(request, assessment, poor);
        String llm = callLlm("你是公路技术状况评定专家，请解释 MQI/PQI/PCI 等指标并指出重点问题路段。", fallback, data);

        return response("评定结果分析", llm, fallback, data);
    }

    @Override
    public AgentMapQueryResponse mapQuery(AgentMapQueryRequest request) {
        AgentMapQueryResponse response = new AgentMapQueryResponse();
        String message = request.getMessage() == null ? "" : request.getMessage();

        boolean diseaseIntent = containsAny(message, "病害", "裂缝", "坑槽", "车辙", "沉陷");
        if (diseaseIntent) {
            response.setObjectType("DISEASE");
            response.setHighlightIds(dataQueryService.findDiseaseIdsForMapQuery(
                    request.getRouteCode(),
                    request.getDiseaseType(),
                    request.getSeverity()
            ));
            response.setQueryHint("已按病害条件查询地图高亮对象");
            return response;
        }

        response.setObjectType("ASSESSMENT");
        response.setHighlightIds(dataQueryService.findAssessmentIdsForMapQuery(
                request.getRouteCode(),
                request.getYear(),
                request.getGrade(),
                request.getIndexCode()
        ));
        response.setQueryHint("已按评定结果条件查询地图高亮对象");
        return response;
    }

    @Override
    public AgentAnalysisResponse generateAssessmentReport(AgentAnalysisRequest request) {
        return traced("ASSESSMENT_REPORT", traceMessage("评定报告", request), () -> generateAssessmentReportInternal(request));
    }

    private AgentAnalysisResponse generateAssessmentReportInternal(AgentAnalysisRequest request) {
        AgentAnalysisResponse route = analyzeRouteInternal(request);
        AgentAnalysisResponse disease = analyzeDiseaseInternal(request);
        AgentAnalysisResponse assessment = analyzeAssessmentInternal(request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("route", route.getData());
        data.put("disease", disease.getData());
        data.put("assessment", assessment.getData());

        String fallback =
                "# 公路技术状况评定分析报告草稿\n\n" +
                "## 一、路线概况\n\n" + route.getMarkdown() + "\n\n" +
                "## 二、病害分析\n\n" + disease.getMarkdown() + "\n\n" +
                "## 三、评定结果分析\n\n" + assessment.getMarkdown() + "\n\n" +
                "## 四、建议\n\n" +
                "1. 优先关注评定等级为次、差的评定单元。\n" +
                "2. 对重度病害集中路段进行现场复核。\n" +
                "3. 对 PCI 较低路段优先安排路面修复或预防养护。\n" +
                "4. 后续结合交通量、管养等级和预算进行养护优先级排序。\n";

        String llm = callLlm("你是公路养护报告编写专家，请生成结构化 Markdown 报告草稿。", fallback, data);
        return response("评定分析报告草稿", llm, fallback, data);
    }

    private AgentAnalysisResponse traced(String requestType, String message, Supplier<AgentAnalysisResponse> supplier) {
        AiTraceContext trace = AiTraceContext.start(requestType, message);
        try {
            AgentAnalysisResponse response = supplier.get();
            trace.setMode(response.getMode());
            trace.setStatus("SUCCESS");
            trace.setFallback(!"LLM".equals(response.getMode()));
            trace.step("business_analysis", "业务数据分析").success(1);
            trace.step("analysis_generate", "生成分析结果").success(1);
            trace.finish();
            response.setTrace(trace.toMap());
            saveTrace(trace);
            return response;
        } catch (RuntimeException e) {
            trace.setMode("FAILED");
            trace.setStatus("FAILED");
            trace.setFallback(true);
            trace.setError(e.getMessage());
            trace.finish();
            saveTrace(trace);
            throw e;
        }
    }

    private void saveTrace(AiTraceContext trace) {
        try {
            aiTraceService.save(trace);
        } catch (Exception e) {
            log.warn("[AI-ANALYSIS] save trace failed traceId={} error={}", trace.getTraceId(), e.getMessage(), e);
        }
    }

    private String traceMessage(String label, AgentAnalysisRequest request) {
        return label + " " + nvl(request == null ? null : request.getRouteCode(), "全部路线") + " " + nvl(request == null ? null : request.getYear(), "全部年度");
    }

    private AgentAnalysisResponse response(String title, String llm, String fallback, Map<String, Object> data) {
        AgentAnalysisResponse response = new AgentAnalysisResponse();
        response.setTitle(title);
        response.setMarkdown(llm == null || llm.trim().isEmpty() ? fallback : llm);
        response.setSummary(firstLine(response.getMarkdown()));
        response.setMode(llm == null || llm.trim().isEmpty() ? "LOCAL_RULE" : "LLM");
        response.setData(data);
        return response;
    }

    private String buildRouteSummary(AgentAnalysisRequest request, Map<String, Object> route, Map<String, Object> disease,
                                     Map<String, Object> assessment, List<Map<String, Object>> poor) {
        return "路线：" + nvl(request.getRouteCode(), "全部") + "\n" +
                "年度：" + nvl(request.getYear(), "全部") + "\n" +
                "路线数量：" + route.get("route_count") + "，总里程：" + route.get("route_length_km") + " km。\n" +
                "病害总数：" + disease.get("disease_count") + "，重度病害：" + disease.get("heavy_count") + "。\n" +
                "平均 MQI：" + assessment.get("avg_mqi") + "，平均 PQI：" + assessment.get("avg_pqi") + "，平均 PCI：" + assessment.get("avg_pci") + "。\n" +
                "次差或低分重点路段数量：" + poor.size() + "。建议优先复核低 PCI、重度病害集中的评定单元。";
    }

    private String buildDiseaseSummary(AgentAnalysisRequest request, Map<String, Object> disease, List<Map<String, Object>> top) {
        StringBuilder sb = new StringBuilder();
        sb.append("病害统计：总数 ").append(disease.get("disease_count"))
                .append("，重度 ").append(disease.get("heavy_count"))
                .append("，中度 ").append(disease.get("medium_count"))
                .append("，轻度 ").append(disease.get("light_count")).append("。\n");
        sb.append("病害热点 TOP").append(top.size()).append("：\n");
        for (int i = 0; i < top.size(); i++) {
            Map<String, Object> item = top.get(i);
            sb.append(i + 1).append(". ")
                    .append(item.get("route_code"))
                    .append(" K").append(item.get("start_stake")).append("-K").append(item.get("end_stake"))
                    .append("，病害 ").append(item.get("disease_count"))
                    .append("，重度 ").append(item.get("heavy_count"))
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildAssessmentSummary(AgentAnalysisRequest request, Map<String, Object> assessment, List<Map<String, Object>> poor) {
        return "评定结果统计：总数 " + assessment.get("assessment_count") +
                "，平均 MQI " + assessment.get("avg_mqi") +
                "，平均 PQI " + assessment.get("avg_pqi") +
                "，平均 PCI " + assessment.get("avg_pci") + "。\n" +
                "等级分布：优 " + assessment.get("excellent_count") +
                "，良 " + assessment.get("good_count") +
                "，中 " + assessment.get("medium_count") +
                "，次 " + assessment.get("poor_count") +
                "，差 " + assessment.get("bad_count") + "。\n" +
                "低分重点对象数量：" + poor.size() + "。";
    }

    private String callLlm(String systemPrompt, String fallbackText, Map<String, Object> data) {
        String prompt = fallbackText + "\n\n结构化数据：\n" + data;
        return llmClient.chat(systemPrompt, prompt);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int idx = text.indexOf('\n');
        return idx < 0 ? text : text.substring(0, idx);
    }

    private String nvl(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
