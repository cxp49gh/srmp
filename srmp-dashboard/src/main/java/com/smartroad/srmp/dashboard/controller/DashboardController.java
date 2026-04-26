package com.smartroad.srmp.dashboard.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("srmp-dashboard ok");
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@RequestParam(required = false) String routeCode,
                                           @RequestParam(required = false) Integer year) {
        return R.ok(dashboardService.overview(routeCode, year));
    }

    @GetMapping("/disease-summary")
    public R<Map<String, Object>> diseaseSummary(@RequestParam(required = false) String routeCode) {
        return R.ok(dashboardService.diseaseSummary(routeCode));
    }

    @GetMapping("/assessment-summary")
    public R<Map<String, Object>> assessmentSummary(@RequestParam(required = false) String routeCode,
                                                    @RequestParam(required = false) Integer year) {
        return R.ok(dashboardService.assessmentSummary(routeCode, year));
    }
}
