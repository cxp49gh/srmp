package com.smartroad.srmp.agent.outline.service;

import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncConfigRequest;
import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncRunRequest;

import java.util.List;
import java.util.Map;

public interface OutlineAutoSyncService {
    List<Map<String, Object>> configs();
    Map<String, Object> config(String id);
    Map<String, Object> saveConfig(OutlineAutoSyncConfigRequest request);
    Map<String, Object> updateConfig(String id, OutlineAutoSyncConfigRequest request);
    Map<String, Object> runNow(String id, OutlineAutoSyncRunRequest request);
    List<Map<String, Object>> runs(String configId, Integer limit);
    Map<String, Object> handleWebhook(String secret, Map<String, Object> payload);
    Map<String, Object> runDueConfigs();
}
