package com.smartroad.srmp.agent.service;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentMapQueryRequest;
import com.smartroad.srmp.agent.vo.AgentAnalysisResponse;
import com.smartroad.srmp.agent.vo.AgentMapQueryResponse;

public interface AgentAnalysisService {
    AgentAnalysisResponse analyzeRoute(AgentAnalysisRequest request);

    AgentAnalysisResponse analyzeDisease(AgentAnalysisRequest request);

    AgentAnalysisResponse analyzeAssessment(AgentAnalysisRequest request);

    AgentMapQueryResponse mapQuery(AgentMapQueryRequest request);

    AgentAnalysisResponse generateAssessmentReport(AgentAnalysisRequest request);
}
