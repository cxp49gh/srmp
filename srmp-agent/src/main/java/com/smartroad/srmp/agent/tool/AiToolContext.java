package com.smartroad.srmp.agent.tool;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import lombok.Data;

import java.util.Map;

@Data
public class AiToolContext {
    private String tenantId;
    private String traceId;
    private String userQuestion;
    private MapAiContext mapContext;
    private Map<String, Object> options;
}
