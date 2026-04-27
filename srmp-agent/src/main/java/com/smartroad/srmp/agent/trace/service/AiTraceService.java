package com.smartroad.srmp.agent.trace.service;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import java.util.List; import java.util.Map;
public interface AiTraceService {
    void save(AiTraceContext trace);
    List<Map<String, Object>> list(String status, String keyword, Integer limit);
    Map<String, Object> detail(String traceId);
    List<Map<String, Object>> steps(String traceId);
}
