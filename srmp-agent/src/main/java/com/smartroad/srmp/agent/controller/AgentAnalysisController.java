package com.smartroad.srmp.agent.controller;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentMapQueryRequest;
import com.smartroad.srmp.agent.service.AgentAnalysisService;
import com.smartroad.srmp.agent.vo.AgentAnalysisResponse;
import com.smartroad.srmp.agent.vo.AgentMapQueryResponse;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/agent")
public class AgentAnalysisController {

    @Resource
    private AgentAnalysisService agentAnalysisService;

    @PostMapping("/analyze/route")
    public R<AgentAnalysisResponse> analyzeRoute(@RequestBody AgentAnalysisRequest request) {
        return R.ok(agentAnalysisService.analyzeRoute(request));
    }

    @PostMapping("/analyze/disease")
    public R<AgentAnalysisResponse> analyzeDisease(@RequestBody AgentAnalysisRequest request) {
        return R.ok(agentAnalysisService.analyzeDisease(request));
    }

    @PostMapping("/analyze/assessment")
    public R<AgentAnalysisResponse> analyzeAssessment(@RequestBody AgentAnalysisRequest request) {
        return R.ok(agentAnalysisService.analyzeAssessment(request));
    }

    @PostMapping("/map-query")
    public R<AgentMapQueryResponse> mapQuery(@RequestBody AgentMapQueryRequest request) {
        return R.ok(agentAnalysisService.mapQuery(request));
    }

    @PostMapping("/report/assessment")
    public R<AgentAnalysisResponse> assessmentReport(@RequestBody AgentAnalysisRequest request) {
        return R.ok(agentAnalysisService.generateAssessmentReport(request));
    }
}
