package com.smartroad.srmp.agent.map.solution.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MapObjectSolutionQualityCheck {
    private boolean passed;
    private List<String> warnings = new ArrayList<>();
    private List<MapObjectSolutionQualityItem> items = new ArrayList<>();
}
