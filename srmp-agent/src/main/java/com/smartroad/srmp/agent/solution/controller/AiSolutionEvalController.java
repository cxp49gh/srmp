package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.service.AiSolutionEvalService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution/eval")
public class AiSolutionEvalController {

    @Resource
    private AiSolutionEvalService aiSolutionEvalService;

    @GetMapping("/cases")
    public R<List<Map<String, Object>>> cases() {
        return R.ok(aiSolutionEvalService.defaultCases());
    }

    @PostMapping("/run")
    public R<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok(aiSolutionEvalService.run(request));
    }
}
