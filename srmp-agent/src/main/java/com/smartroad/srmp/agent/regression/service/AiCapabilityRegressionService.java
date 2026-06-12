package com.smartroad.srmp.agent.regression.service;

import java.util.List;
import java.util.Map;

public interface AiCapabilityRegressionService {
    List<Map<String, Object>> defaultCases();

    Map<String, Object> run(Map<String, Object> request);
}
