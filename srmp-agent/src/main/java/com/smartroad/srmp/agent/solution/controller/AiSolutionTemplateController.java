package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateImportRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateQuery;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplateService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution/templates")
public class AiSolutionTemplateController {

    @Resource
    private AiSolutionTemplateService aiSolutionTemplateService;

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody AiSolutionTemplateRequest request) {
        return R.ok(aiSolutionTemplateService.create(request));
    }

    @PostMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestBody(required = false) AiSolutionTemplateQuery query) {
        return R.ok(aiSolutionTemplateService.list(query));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable String id) {
        return R.ok(aiSolutionTemplateService.detail(id));
    }

    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> versions(@PathVariable String id) {
        return R.ok(aiSolutionTemplateService.versions(id));
    }

    @PostMapping("/import-from-knowledge")
    public R<Map<String, Object>> importFromKnowledge(@RequestBody AiSolutionTemplateImportRequest request) {
        return R.ok(aiSolutionTemplateService.importFromKnowledge(request));
    }

    @PostMapping("/{id}/disable")
    public R<Map<String, Object>> disable(@PathVariable String id) {
        return R.ok(aiSolutionTemplateService.disable(id));
    }
}
