package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftStatusUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftUpdateRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionDraftService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution/tasks")
public class AiSolutionDraftController {

    @Resource
    private AiSolutionDraftService aiSolutionDraftService;

    @PostMapping("/map-object-drafts")
    public R<Map<String, Object>> saveMapObjectDraft(@RequestBody AiSolutionDraftSaveRequest request) {
        return R.ok(aiSolutionDraftService.saveMapObjectDraft(request));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> updateDraft(@PathVariable String id,
                                              @RequestBody AiSolutionDraftUpdateRequest request) {
        return R.ok(aiSolutionDraftService.updateDraft(id, request));
    }

    @PostMapping("/{id}/draft-status")
    public R<Map<String, Object>> updateDraftStatus(@PathVariable String id,
                                                    @RequestBody AiSolutionDraftStatusUpdateRequest request) {
        return R.ok(aiSolutionDraftService.updateDraftStatus(id, request));
    }

    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> versions(@PathVariable String id) {
        return R.ok(aiSolutionDraftService.versions(id));
    }
}
