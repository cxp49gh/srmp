package com.smartroad.srmp.dashboard.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("srmp-dashboard ok");
    }
}
