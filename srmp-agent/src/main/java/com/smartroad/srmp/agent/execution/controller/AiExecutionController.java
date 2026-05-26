package com.smartroad.srmp.agent.execution.controller;

import com.smartroad.srmp.agent.execution.service.AiExecutionService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/executions")
public class AiExecutionController {
    @Resource
    private AiExecutionService aiExecutionService;

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Map<String, Object> query) {
        return R.ok(aiExecutionService.list(query == null ? new LinkedHashMap<String, Object>() : query));
    }

    @GetMapping("/{traceId}")
    public R<Map<String, Object>> detail(@PathVariable String traceId) {
        return R.ok(aiExecutionService.detail(traceId));
    }
}
