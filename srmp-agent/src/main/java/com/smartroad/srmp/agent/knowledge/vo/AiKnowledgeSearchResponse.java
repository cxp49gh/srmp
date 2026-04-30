package com.smartroad.srmp.agent.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiKnowledgeSearchResponse {
    private String query;
    private List<AiKnowledgeSearchHit> hits = new ArrayList<>();
}
