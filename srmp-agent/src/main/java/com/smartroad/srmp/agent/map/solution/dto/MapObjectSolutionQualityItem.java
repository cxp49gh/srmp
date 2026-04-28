package com.smartroad.srmp.agent.map.solution.dto;

import lombok.Data;

@Data
public class MapObjectSolutionQualityItem {
    private String name;
    private boolean passed;
    private String message;
    private String level;
}
