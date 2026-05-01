package com.smartroad.srmp.agent.embedding.controller;

import com.smartroad.srmp.agent.embedding.EmbeddingHealthResponse;
import com.smartroad.srmp.agent.embedding.EmbeddingHealthService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/ai/embedding")
public class EmbeddingHealthController {

    @Resource
    private EmbeddingHealthService embeddingHealthService;

    @GetMapping("/health")
    public R<EmbeddingHealthResponse> health() {
        return R.ok(embeddingHealthService.check());
    }
}
