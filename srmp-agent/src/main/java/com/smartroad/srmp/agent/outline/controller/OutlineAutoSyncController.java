package com.smartroad.srmp.agent.outline.controller;

import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncConfigRequest;
import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncRunRequest;
import com.smartroad.srmp.agent.outline.service.OutlineAutoSyncService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outline/auto-sync")
public class OutlineAutoSyncController {

    @Resource
    private OutlineAutoSyncService outlineAutoSyncService;

    @GetMapping("/configs")
    public R<List<Map<String, Object>>> configs() {
        return R.ok(outlineAutoSyncService.configs());
    }

    @GetMapping("/configs/{id}")
    public R<Map<String, Object>> config(@PathVariable String id) {
        return R.ok(outlineAutoSyncService.config(id));
    }

    @PostMapping("/configs")
    public R<Map<String, Object>> saveConfig(@RequestBody OutlineAutoSyncConfigRequest request) {
        return R.ok(outlineAutoSyncService.saveConfig(request));
    }

    @PutMapping("/configs/{id}")
    public R<Map<String, Object>> updateConfig(@PathVariable String id, @RequestBody OutlineAutoSyncConfigRequest request) {
        return R.ok(outlineAutoSyncService.updateConfig(id, request));
    }

    @PostMapping("/configs/{id}/run")
    public R<Map<String, Object>> runNow(@PathVariable String id, @RequestBody(required = false) OutlineAutoSyncRunRequest request) {
        return R.ok(outlineAutoSyncService.runNow(id, request));
    }

    @GetMapping("/runs")
    public R<List<Map<String, Object>>> runs(@RequestParam(required = false) String configId, @RequestParam(required = false) Integer limit) {
        return R.ok(outlineAutoSyncService.runs(configId, limit));
    }

    @PostMapping("/webhook")
    public R<Map<String, Object>> webhook(@RequestHeader(value = "X-Outline-Webhook-Secret", required = false) String secret,
                                          @RequestBody Map<String, Object> payload) {
        return R.ok(outlineAutoSyncService.handleWebhook(secret, payload));
    }

    @PostMapping("/scan-due")
    public R<Map<String, Object>> scanDue() {
        return R.ok(outlineAutoSyncService.runDueConfigs());
    }
}
