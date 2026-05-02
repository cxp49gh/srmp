package com.smartroad.srmp.agent.outline.controller;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeReindexRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeReindexService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeReindexResponse;
import com.smartroad.srmp.agent.outline.dto.OutlineCollectionDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineDocumentDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;
import com.smartroad.srmp.agent.outline.service.OutlineSyncService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outline")
public class OutlineSyncController {

    @Resource
    private OutlineSyncService outlineSyncService;

    @Resource
    private AiKnowledgeReindexService aiKnowledgeReindexService;

    @GetMapping("/collections")
    public R<List<OutlineCollectionDTO>> collections() {
        return R.ok(outlineSyncService.collections());
    }

    @PostMapping("/documents/list")
    public R<List<OutlineDocumentDTO>> documents(@RequestBody Map request) {
        String collectionId = request == null || request.get("collectionId") == null ? null : String.valueOf(request.get("collectionId"));
        return R.ok(outlineSyncService.documents(collectionId, readInt(request == null ? null : request.get("limit")), readInt(request == null ? null : request.get("offset"))));
    }

    @PostMapping("/sync")
    public R<Map> sync(@RequestBody OutlineSyncRequest request) {
        return R.ok(outlineSyncService.sync(request));
    }

    /**
     * Outline 专用补向量入口。
     * 默认只处理 ai_knowledge_chunk.embedding 为空的 OUTLINE chunk；传 force=true 可强制重建。
     */
    @PostMapping("/vectorize")
    public R<AiKnowledgeReindexResponse> vectorize(@RequestBody(required = false) AiKnowledgeReindexRequest request) {
        AiKnowledgeReindexRequest req = request == null ? new AiKnowledgeReindexRequest() : request;
        req.setSourceType("OUTLINE");
        if (req.getLimit() == null || req.getLimit() <= 0) {
            req.setLimit(200);
        }
        return R.ok(aiKnowledgeReindexService.reindex(req));
    }

    /**
     * 查看 Outline 文档在 ai_knowledge_document / ai_knowledge_chunk 中的真实落库与向量化情况。
     */
    @GetMapping("/knowledge-stats")
    public R<Map<String, Object>> knowledgeStats() {
        return R.ok(outlineSyncService.knowledgeStats());
    }

    @GetMapping("/sync-tasks")
    public R<List<Map<String, Object>>> tasks(@RequestParam(required = false) Integer limit) {
        return R.ok(outlineSyncService.tasks(limit));
    }

    @GetMapping("/sync-tasks/{id}")
    public R<Map<String, Object>> task(@PathVariable String id) {
        return R.ok(outlineSyncService.task(id));
    }

    @GetMapping("/sync-tasks/{id}/details")
    public R<List<Map<String, Object>>> details(@PathVariable String id,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Integer limit) {
        return R.ok(outlineSyncService.details(id, status, limit));
    }

    @PostMapping("/sync-tasks/{id}/retry-failed")
    public R<Map<String, Object>> retryFailed(@PathVariable String id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        return R.ok(outlineSyncService.retryFailed(id, force));
    }

    private Integer readInt(Object value) {
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }
}
