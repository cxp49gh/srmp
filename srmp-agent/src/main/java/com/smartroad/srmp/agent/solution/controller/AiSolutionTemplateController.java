package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateImportRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateMatchPreviewRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateQuery;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRenderPreviewRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateStatusRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTemplateVersionRequest;
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

    @PostMapping("/match-preview")
    public R<Map<String, Object>> matchPreview(@RequestBody AiSolutionTemplateMatchPreviewRequest request) {
        return R.ok(aiSolutionTemplateService.matchPreview(request));
    }

    @PostMapping("/{id}/render-preview")
    public R<Map<String, Object>> renderPreview(@PathVariable String id,
                                                @RequestBody AiSolutionTemplateRenderPreviewRequest request) {
        return R.ok(aiSolutionTemplateService.renderPreview(id, request));
    }

    @PostMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable String id,
                                               @RequestBody AiSolutionTemplateStatusRequest request) {
        return R.ok(aiSolutionTemplateService.updateStatus(id, request));
    }

    @PostMapping("/{id}/default")
    public R<Map<String, Object>> setDefault(@PathVariable String id) {
        return R.ok(aiSolutionTemplateService.setDefault(id));
    }

    @PostMapping("/{id}/versions")
    public R<Map<String, Object>> createVersion(@PathVariable String id,
                                                @RequestBody AiSolutionTemplateVersionRequest request) {
        return R.ok(aiSolutionTemplateService.createVersion(id, request));
    }
}
