package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.service.AiSolutionQualityService;
import com.smartroad.srmp.common.core.R;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution/tasks")
public class AiSolutionQualityController {

    @Resource
    private AiSolutionQualityService aiSolutionQualityService;

    @PostMapping("/{id}/quality-check")
    public R<Map<String, Object>> qualityCheck(@PathVariable String id) {
        return R.ok(aiSolutionQualityService.check(id));
    }

    @GetMapping("/{id}/quality-result")
    public R<Map<String, Object>> qualityResult(@PathVariable String id) {
        return R.ok(aiSolutionQualityService.qualityResult(id));
    }

    @GetMapping("/{id}/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable String id) {
        String markdown = aiSolutionQualityService.exportMarkdown(id);
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("ai-solution-" + id + ".md", StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }
}