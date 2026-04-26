package com.smartroad.srmp.agent.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AgentChatRequest {
    private String message;
    private Map context;

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
