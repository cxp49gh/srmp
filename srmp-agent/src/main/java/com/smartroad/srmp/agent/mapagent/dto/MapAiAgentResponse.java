package com.smartroad.srmp.agent.mapagent.dto;

import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.tool.AiToolResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MapAiAgentResponse {
    private String answer;
    private String mode = "MAP_AI_AGENT";
    private String intent;
    private MapAiContext mapContext;
    private List<AiToolResult> toolResults = new ArrayList<>();
    private List<AiKnowledgeSearchHit> knowledgeSources = new ArrayList<>();
    /**
     * Phase36.1：前端直接读取的参考资料字段。
     * 与 knowledgeSources 保持同一份数据，避免前端只读取 sources 时为空。
     */
    private List<AiKnowledgeSearchHit> sources = new ArrayList<>();
    private Map<String, Object> trace;
    private Map<String, Object> data;
}
