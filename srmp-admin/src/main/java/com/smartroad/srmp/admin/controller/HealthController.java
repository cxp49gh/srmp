package com.smartroad.srmp.admin.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("system", "SmartRoad Maintenance Platform");
        data.put("status", "UP");
        data.put("phase", "phase1-skeleton");
        return R.ok(data);
    }
}
