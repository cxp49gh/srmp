package com.smartroad.srmp.agent.map.solution.service;

import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;

public interface MapObjectSolutionService {
    MapObjectSolutionResponse generate(MapObjectSolutionRequest request);
}
