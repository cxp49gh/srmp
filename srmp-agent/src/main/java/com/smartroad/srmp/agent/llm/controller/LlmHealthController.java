package com.smartroad.srmp.agent.llm.controller;

import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/llm")
public class LlmHealthController {

    @Resource
    private LlmClient llmClient;

    @GetMapping("/health")
    public R<Map<String, Object>> health(@RequestParam(defaultValue = "false") boolean probe) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.putAll(llmClient.diagnostics());
        data.put("enabled", llmClient.enabled());

        if (probe) {
            long start = System.currentTimeMillis();
            String answer = llmClient.chat("你是健康检查助手，只返回 OK。", "请只返回 OK");
            data.clear();
            data.putAll(llmClient.diagnostics());
            data.put("enabled", llmClient.enabled());
            data.put("available", answer != null && answer.trim().length() > 0);
            data.put("probeAnswerPreview", answer == null ? "" : answer.substring(0, Math.min(answer.length(), 80)));
            data.put("probeCostMs", System.currentTimeMillis() - start);
        }

        if (!data.containsKey("available")) {
            data.put("available", Boolean.TRUE.equals(data.get("success")));
        }
        return R.ok(data);
    }
}
