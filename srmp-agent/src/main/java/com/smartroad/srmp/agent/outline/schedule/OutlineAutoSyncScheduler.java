package com.smartroad.srmp.agent.outline.schedule;

import com.smartroad.srmp.agent.outline.service.OutlineAutoSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "srmp.outline.auto-sync", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutlineAutoSyncScheduler {

    @Resource
    private OutlineAutoSyncService outlineAutoSyncService;

    @Scheduled(fixedDelayString = "${srmp.outline.auto-sync.scan-interval-ms:60000}")
    public void scanDueConfigs() {
        try {
            outlineAutoSyncService.runDueConfigs();
        } catch (Exception e) {
            log.warn("Outline auto sync scheduler failed: {}", e.getMessage());
        }
    }
}
