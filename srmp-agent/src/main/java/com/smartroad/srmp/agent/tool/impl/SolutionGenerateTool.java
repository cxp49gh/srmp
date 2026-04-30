package com.smartroad.srmp.agent.tool.impl;

import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SolutionGenerateTool implements AiTool {
    @Override
    public String name() { return "solution.generateDraft"; }
    @Override
    public String description() { return "生成方案草稿。当前工具返回能力提示，正式生成仍复用地图对象/区域方案接口。"; }
    @Override
    public boolean supports(AiToolContext context) { return true; }
    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "请使用现有 /api/agent/map-object/solution 或 /api/gis/map-region/solution 生成方案草稿");
        return AiToolResult.success(name(), "方案生成工具已注册", data, 1, System.currentTimeMillis() - start);
    }
}
