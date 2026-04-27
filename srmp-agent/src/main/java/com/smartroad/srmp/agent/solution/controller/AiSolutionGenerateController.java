package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionGenerateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTaskQuery;
import com.smartroad.srmp.agent.solution.service.AiSolutionGenerateService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution")
public class AiSolutionGenerateController {

    @Resource
    private AiSolutionGenerateService aiSolutionGenerateService;

    @PostMapping("/generate")
    public R<Map<String, Object>> generate(@RequestBody AiSolutionGenerateRequest request) {
        return R.ok(aiSolutionGenerateService.generate(request));
    }

    @PostMapping("/tasks/list")
    public R<List<Map<String, Object>>> tasks(@RequestBody(required = false) AiSolutionTaskQuery query) {
        return R.ok(aiSolutionGenerateService.tasks(query));
    }

    @GetMapping("/tasks/{id}")
    public R<Map<String, Object>> task(@PathVariable String id) {
        return R.ok(aiSolutionGenerateService.task(id));
    }

    @GetMapping("/tasks/{id}/sources")
    public R<List<Map<String, Object>>> sources(@PathVariable String id) {
        return R.ok(aiSolutionGenerateService.sources(id));
    }
}
