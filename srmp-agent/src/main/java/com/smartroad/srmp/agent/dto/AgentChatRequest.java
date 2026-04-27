package com.smartroad.srmp.agent.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AgentChatRequest {
    private String message;
    private Map context;

    /**
     * 当前地图选中对象，作为 AI 问答的上下文。
     * 支持 ROAD_ROUTE, ROAD_SECTION, EVALUATION_UNIT, DISEASE, ASSESSMENT_RESULT
     */
    private Map<String, Object> mapObject;

    /**
     * AI 问答增强选项。
     *
     * 示例：
     * {
     *   "useBusinessData": true,
     *   "useKnowledge": true,
     *   "useOutline": true,
     *   "topK": 5
     * }
     */
    private Map options;
}
