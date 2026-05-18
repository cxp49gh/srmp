package com.smartroad.srmp.agent.outline.controller;

import com.smartroad.srmp.agent.outline.service.OutlineGovernanceService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/outline/governance")
public class OutlineGovernanceController {

    @Resource
    private OutlineGovernanceService outlineGovernanceService;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(outlineGovernanceService.dashboard());
    }
}
