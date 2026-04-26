package com.smartroad.srmp.agent.service;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;

import java.util.List;
import java.util.Map;

public interface AgentDataQueryService {
    Map<String, Object> routeSummary(AgentAnalysisRequest request);

    Map<String, Object> diseaseSummary(AgentAnalysisRequest request);

    Map<String, Object> assessmentSummary(AgentAnalysisRequest request);

    List<Map<String, Object>> topDiseaseUnits(AgentAnalysisRequest request, int limit);

    List<Map<String, Object>> poorAssessmentResults(AgentAnalysisRequest request, int limit);

    List<String> findAssessmentIdsForMapQuery(String routeCode, Integer year, String grade, String indexCode);

    List<String> findDiseaseIdsForMapQuery(String routeCode, String diseaseType, String severity);
}
