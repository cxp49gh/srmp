package com.smartroad.srmp.agent.solution.service;

import java.util.Map;

public interface AiSolutionQualityService {
    Map<String, Object> check(String taskId);

    Map<String, Object> qualityResult(String taskId);

    String exportMarkdown(String taskId);
}