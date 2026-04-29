package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateImportRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateMatchPreviewRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateQuery;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRenderPreviewRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateStatusRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateVersionRequest;

import java.util.List;
import java.util.Map;

public interface AiSolutionTemplateService {
    Map<String, Object> create(AiSolutionTemplateRequest request);

    List<Map<String, Object>> list(AiSolutionTemplateQuery query);

    Map<String, Object> detail(String id);

    List<Map<String, Object>> versions(String templateId);

    Map<String, Object> importFromKnowledge(AiSolutionTemplateImportRequest request);

    Map<String, Object> disable(String id);

    Map<String, Object> matchPreview(AiSolutionTemplateMatchPreviewRequest request);

    Map<String, Object> renderPreview(String id, AiSolutionTemplateRenderPreviewRequest request);

    Map<String, Object> updateStatus(String id, AiSolutionTemplateStatusRequest request);

    Map<String, Object> setDefault(String id);

    Map<String, Object> createVersion(String id, AiSolutionTemplateVersionRequest request);
}
