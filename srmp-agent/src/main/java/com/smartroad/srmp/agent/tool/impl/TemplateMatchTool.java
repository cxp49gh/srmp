package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TemplateMatchTool implements AiTool {
    @Resource
    private AiSolutionTemplatePipelineService pipelineService;

    @Override
    public String name() { return "template.match"; }
    @Override
    public String description() { return "检查方案模板匹配情况"; }
    @Override
    public boolean supports(AiToolContext context) { return true; }
    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", pipelineService != null);
        data.put("note", "模板匹配由现有 AiSolutionTemplatePipelineService 在方案生成时执行");
        return AiToolResult.success(name(), "模板匹配工具可用", data, 1, System.currentTimeMillis() - start);
    }
}
