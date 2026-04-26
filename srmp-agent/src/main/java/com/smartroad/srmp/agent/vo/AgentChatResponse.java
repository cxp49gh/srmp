package com.smartroad.srmp.agent.vo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentChatResponse {
    private String answer;
    private String mode;
    private Map<String, Object> data = new HashMap<>();
}
