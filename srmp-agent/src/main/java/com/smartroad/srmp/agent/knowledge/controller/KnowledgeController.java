package com.smartroad.srmp.agent.knowledge.controller;

import com.smartroad.srmp.agent.knowledge.dto.KnowledgeDocumentRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeService knowledgeService;

    @PostMapping("/documents")
    public R<Map<String, String>> create(@RequestBody KnowledgeDocumentRequest request) {
        String id = knowledgeService.createDocument(request);
        return R.ok(Collections.singletonMap("id", id));
    }

    @PostMapping("/search")
    public R<List<KnowledgeSearchResult>> search(@RequestBody KnowledgeSearchRequest request) {
        return R.ok(knowledgeService.search(request));
    }

    @PostMapping("/ask")
    public R<Map> ask(@RequestBody KnowledgeSearchRequest request) {
        return R.ok(knowledgeService.ask(request));
    }
}
