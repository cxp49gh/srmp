package com.smartroad.srmp.agent.execution.service;

import java.util.List;
import java.util.Map;

public interface AiExecutionService {
    List<Map<String, Object>> list(Map<String, Object> query);

    Map<String, Object> detail(String traceId);
}
