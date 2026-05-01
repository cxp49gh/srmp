package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.dto.AiSolutionAiContextUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTaskVersionRestoreRequest;
import java.util.List;
import java.util.Map;

public interface AiSolutionTaskClosureService {
    Map<String, Object> updateAiContext(String taskId, AiSolutionAiContextUpdateRequest request);
    Map<String, Object> aiContext(String taskId);
    List<Map<String, Object>> statusTimeline(String taskId);
    Map<String, Object> restoreVersion(String taskId, Integer versionNo, AiSolutionTaskVersionRestoreRequest request);
    String exportMarkdownV2(String taskId);
}
