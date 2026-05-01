package com.smartroad.srmp.agent.knowledge.controller;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeIngestService;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeStatsResponse;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/knowledge")
public class AiKnowledgeController {

    @Resource
    private AiKnowledgeIngestService aiKnowledgeIngestService;

    @Resource
    private AiKnowledgeRetrieverService aiKnowledgeRetrieverService;

    @PostMapping("/ingest/markdown")
    public R<Map<String, Object>> ingestMarkdown(@RequestBody AiKnowledgeIngestMarkdownRequest request) {
        return R.ok(aiKnowledgeIngestService.ingestMarkdown(request));
    }

    @PostMapping("/search")
    public R<AiKnowledgeSearchResponse> search(@RequestBody AiKnowledgeSearchRequest request) {
        return R.ok(aiKnowledgeRetrieverService.search(request));
    }

    /**
     * Phase36 补强：知识库统计，用于验证知识数据、embedding 和向量能力是否可用。
     */
    @GetMapping("/stats")
    public R<AiKnowledgeStatsResponse> stats(@RequestParam(value = "tenantId", required = false) String tenantId) {
        return R.ok(aiKnowledgeRetrieverService.stats(tenantId));
    }
}
