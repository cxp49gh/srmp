package com.smartroad.srmp.agent.trace.controller;

import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List; import java.util.Map;

@RestController
@RequestMapping("/api/ai/traces")
public class AiTraceController {
    @Resource private AiTraceService aiTraceService;
    @GetMapping public R<List<Map<String,Object>>> list(@RequestParam(required=false) String status, @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer limit) { return R.ok(aiTraceService.list(status, keyword, limit)); }
    @GetMapping("/{traceId}") public R<Map<String,Object>> detail(@PathVariable String traceId) { return R.ok(aiTraceService.detail(traceId)); }
    @GetMapping("/{traceId}/steps") public R<List<Map<String,Object>>> steps(@PathVariable String traceId) { return R.ok(aiTraceService.steps(traceId)); }
}
