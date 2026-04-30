package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SolutionSaveTool implements AiTool {
    @Override
    public String name() { return "solution.saveTask"; }
    @Override
    public String description() { return "保存方案任务。当前工具返回能力提示，正式保存复用阶段三十三草稿接口。"; }
    @Override
    public boolean supports(AiToolContext context) { return true; }
    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "请使用 /api/ai/solution/tasks/map-object-drafts 或区域 drafts 接口保存方案任务");
        return AiToolResult.success(name(), "方案保存工具已注册", data, 1, System.currentTimeMillis() - start);
    }
}
