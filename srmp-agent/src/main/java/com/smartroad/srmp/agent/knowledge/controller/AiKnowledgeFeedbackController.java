package com.smartroad.srmp.agent.knowledge.controller;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeFeedbackCreateRequest;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeFeedbackQuery;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeFeedbackService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/feedback")
public class AiKnowledgeFeedbackController {

    @Resource
    private AiKnowledgeFeedbackService aiKnowledgeFeedbackService;

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody AiKnowledgeFeedbackCreateRequest request) {
        return R.ok(aiKnowledgeFeedbackService.create(request));
    }

    @GetMapping
    public R<List<Map<String, Object>>> list(AiKnowledgeFeedbackQuery query) {
        return R.ok(aiKnowledgeFeedbackService.list(query));
    }
}
