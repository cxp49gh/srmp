package com.smartroad.srmp.agent.solution.template;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TemplatePipelineResult {
    private String markdown;
    private String renderedMarkdown;
    private Map<String, Object> templateMeta = new LinkedHashMap<>();
    private Map<String, Object> variables = new LinkedHashMap<>();
    private List<String> missingVariables = new ArrayList<>();
    private List<String> unusedVariables = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<Map<String, Object>> sourceSummaries = new ArrayList<>();
}
