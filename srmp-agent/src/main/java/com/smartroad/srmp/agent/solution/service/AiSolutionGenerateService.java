package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.dto.AiSolutionGenerateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTaskQuery;

import java.util.List;
import java.util.Map;

public interface AiSolutionGenerateService {
    Map<String, Object> generate(AiSolutionGenerateRequest request);

    Map<String, Object> task(String id);

    List<Map<String, Object>> tasks(AiSolutionTaskQuery query);

    List<Map<String, Object>> sources(String taskId);
}
