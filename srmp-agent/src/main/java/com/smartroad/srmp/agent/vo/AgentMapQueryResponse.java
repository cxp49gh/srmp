package com.smartroad.srmp.agent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentMapQueryResponse {
    private String objectType;
    private String queryHint;
    private List<String> highlightIds = new ArrayList<>();
}
