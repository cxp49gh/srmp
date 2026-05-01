package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionAiContextUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTaskVersionRestoreRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionTaskClosureService;
import com.smartroad.srmp.common.core.R;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution/tasks")
public class AiSolutionTaskClosureController {

    @Resource
    private AiSolutionTaskClosureService aiSolutionTaskClosureService;

    @PostMapping("/{id}/ai-context")
    public R<Map<String, Object>> updateAiContext(@PathVariable String id, @RequestBody(required = false) AiSolutionAiContextUpdateRequest request) {
        return R.ok(aiSolutionTaskClosureService.updateAiContext(id, request));
    }

    @GetMapping("/{id}/ai-context")
    public R<Map<String, Object>> aiContext(@PathVariable String id) {
        return R.ok(aiSolutionTaskClosureService.aiContext(id));
    }

    @GetMapping("/{id}/status-timeline")
    public R<List<Map<String, Object>>> statusTimeline(@PathVariable String id) {
        return R.ok(aiSolutionTaskClosureService.statusTimeline(id));
    }

    @PostMapping("/{id}/versions/{versionNo}/restore")
    public R<Map<String, Object>> restoreVersion(@PathVariable String id, @PathVariable Integer versionNo, @RequestBody(required = false) AiSolutionTaskVersionRestoreRequest request) {
        return R.ok(aiSolutionTaskClosureService.restoreVersion(id, versionNo, request));
    }

    @GetMapping("/{id}/export/markdown-v2")
    public ResponseEntity<byte[]> exportMarkdownV2(@PathVariable String id) {
        String markdown = aiSolutionTaskClosureService.exportMarkdownV2(id);
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("ai-solution-task-" + id + ".md", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
