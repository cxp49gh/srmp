package com.smartroad.srmp.demo.service;

import java.util.List;
import java.util.Map;

public interface DemoDataService {

    Map<String, Object> status(String tenantId, Integer year);

    Map<String, Object> dashboard(String tenantId, Integer year);

    List<Map<String, Object>> routes(String tenantId, Integer year);

    List<Map<String, Object>> quickQuestions();
}