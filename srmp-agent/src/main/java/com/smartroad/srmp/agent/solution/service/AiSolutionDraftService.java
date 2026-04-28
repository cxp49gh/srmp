package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftStatusUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftUpdateRequest;

import java.util.List;
import java.util.Map;

public interface AiSolutionDraftService {
    Map<String, Object> saveMapObjectDraft(AiSolutionDraftSaveRequest request);

    Map<String, Object> saveMapRegionDraft(AiSolutionDraftSaveRequest request);

    Map<String, Object> updateDraft(String taskId, AiSolutionDraftUpdateRequest request);

    Map<String, Object> updateDraftStatus(String taskId, AiSolutionDraftStatusUpdateRequest request);

    List<Map<String, Object>> versions(String taskId);
}
