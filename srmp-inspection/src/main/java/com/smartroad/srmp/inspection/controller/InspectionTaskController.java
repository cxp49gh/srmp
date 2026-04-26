package com.smartroad.srmp.inspection.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-tasks")
public class InspectionTaskController {

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("srmp-inspection ok");
    }
}
