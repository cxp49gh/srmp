package com.smartroad.srmp.agent.vo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentChatResponse {
    private String answer;
    private String mode;
    private Boolean mapObjectUsed;
    private Map<String, Object> mapObject;
    private String mapObjectContext;
    private Map<String, Object> data = new HashMap<>();
}
