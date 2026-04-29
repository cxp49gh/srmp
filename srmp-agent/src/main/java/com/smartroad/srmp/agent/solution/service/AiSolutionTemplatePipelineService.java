package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;

public interface AiSolutionTemplatePipelineService {
    TemplatePipelineResult generate(SolutionTemplateContext context);
}
