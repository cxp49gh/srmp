package com.smartroad.srmp.agent.vo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentAnalysisResponse {
    private String title;
    private String summary;
    private String markdown;
    private String mode;
    private Map<String, Object> data = new HashMap<>();
}
