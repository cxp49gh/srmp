package com.smartroad.srmp.agent.solution.service;

import java.util.Map;

public interface AiMapRegionSolutionGateway {
    Map<String, Object> generate(Map<String, Object> request);
}
