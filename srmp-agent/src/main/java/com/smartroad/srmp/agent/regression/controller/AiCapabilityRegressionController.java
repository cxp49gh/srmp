package com.smartroad.srmp.agent.regression.controller;

import com.smartroad.srmp.agent.regression.service.AiCapabilityRegressionService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/capability-regression")
public class AiCapabilityRegressionController {

    @Resource
    private AiCapabilityRegressionService aiCapabilityRegressionService;

    @GetMapping("/cases")
    public R<List<Map<String, Object>>> cases() {
        return R.ok(aiCapabilityRegressionService.defaultCases());
    }

    @PostMapping("/run")
    public R<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok(aiCapabilityRegressionService.run(request));
    }
}
