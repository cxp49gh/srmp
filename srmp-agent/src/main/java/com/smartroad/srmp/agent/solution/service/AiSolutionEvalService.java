package com.smartroad.srmp.agent.solution.service;

import java.util.List;
import java.util.Map;

public interface AiSolutionEvalService {
    List<Map<String, Object>> defaultCases();

    Map<String, Object> run(Map<String, Object> request);
}
