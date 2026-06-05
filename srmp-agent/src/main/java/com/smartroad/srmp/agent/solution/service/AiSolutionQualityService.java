package com.smartroad.srmp.agent.solution.service;

import java.util.Map;
import java.util.List;

public interface AiSolutionQualityService {
    Map<String, Object> check(String taskId);

    Map<String, Object> qualityResult(String taskId);

    Map<String, Object> evaluateSnapshot(Map<String, Object> task,
                                         List<Map<String, Object>> sources,
                                         String content,
                                         Map<String, Object> baseResult);

    String exportMarkdown(String taskId);
}
