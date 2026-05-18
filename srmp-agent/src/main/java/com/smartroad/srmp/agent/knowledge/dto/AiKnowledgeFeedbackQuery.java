package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

@Data
public class AiKnowledgeFeedbackQuery {
    private String feedbackType;
    private Integer limit = 50;
}
