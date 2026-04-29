package com.smartroad.srmp.agent.solution.template;

import com.smartroad.srmp.agent.trace.AiTraceContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class SolutionTemplateContext {
    private String tenantId;
    private String originType;
    private String objectType;
    private String solutionType;
    private String routeCode;
    private Integer year;
    private String title;
    private String fallbackMarkdown;
    private String templateId;
    private String templateCode;
    private Map<String, Object> mapObject = new LinkedHashMap<>();
    private Map<String, Object> objectSummary = new LinkedHashMap<>();
    private Map<String, Object> regionSummary = new LinkedHashMap<>();
    private Map<String, Object> businessData = new LinkedHashMap<>();
    private List<Map<String, Object>> knowledgeSources = new ArrayList<>();
    private List<Map<String, Object>> outlineSources = new ArrayList<>();
    private Map<String, Object> options = new LinkedHashMap<>();
    private AiTraceContext trace;
}
