package com.smartroad.srmp.demo.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.demo.service.DemoDataService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoDataController {

    @Resource
    private DemoDataService demoDataService;

    @GetMapping("/status")
    public R<Map<String, Object>> status(@RequestParam(required = false, defaultValue = "default") String tenantId,
                                         @RequestParam(required = false, defaultValue = "2026") Integer year) {
        return R.ok(demoDataService.status(tenantId, year));
    }

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard(@RequestParam(required = false, defaultValue = "default") String tenantId,
                                            @RequestParam(required = false, defaultValue = "2026") Integer year) {
        return R.ok(demoDataService.dashboard(tenantId, year));
    }

    @GetMapping("/routes")
    public R<List<Map<String, Object>>> routes(@RequestParam(required = false, defaultValue = "default") String tenantId,
                                               @RequestParam(required = false, defaultValue = "2026") Integer year) {
        return R.ok(demoDataService.routes(tenantId, year));
    }

    @GetMapping("/questions")
    public R<List<Map<String, Object>>> questions() {
        return R.ok(demoDataService.quickQuestions());
    }
}