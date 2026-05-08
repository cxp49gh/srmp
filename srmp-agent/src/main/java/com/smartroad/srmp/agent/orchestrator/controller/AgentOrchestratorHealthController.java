package com.smartroad.srmp.agent.orchestrator.controller;

import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorProperties;
import com.smartroad.srmp.agent.tool.AiToolRegistry;
import com.smartroad.srmp.common.core.R;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase50.4：AI 编排链路健康检查。
 *
 * 用于定位三段链路是否闭合：
 * Java /api/agent/map-agent/run -> LangGraph Runtime -> Java /api/agent/tools/execute。
 */
@RestController
@RequestMapping("/api/agent/orchestrator")
public class AgentOrchestratorHealthController {

    @Resource
    private AgentOrchestratorProperties properties;

    @Resource
    private AiToolRegistry aiToolRegistry;

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("provider", "langgraph");
        data.put("allowWriteTools", properties.getAllowWriteTools());
        data.put("availableProviders", java.util.Collections.singletonList("langgraph"));
        data.put("localToolCount", aiToolRegistry.list().size());
        data.put("langgraphUrl", properties.getLanggraphUrl());
        data.put("langgraphEndpointPath", properties.getLanggraphEndpointPath());
        data.put("langgraphHealth", remoteProbe(properties.getLanggraphHealthPath()));
        data.put("langgraphReady", remoteProbe(properties.getLanggraphReadyPath()));
        return R.ok(data);
    }

    private Map<String, Object> remoteProbe(String path) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate();
            ResponseEntity<Map> entity = restTemplate.getForEntity(url, Map.class);
            data.put("httpStatus", entity.getStatusCodeValue());
            data.put("ok", entity.getStatusCode().is2xxSuccessful());
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("body", entity.getBody());
        } catch (Exception e) {
            data.put("ok", false);
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("error", e.getMessage());
        }
        return data;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    private String buildUrl(String baseUrl, String path) {
        String base = isBlank(baseUrl) ? "http://127.0.0.1:18080" : baseUrl.trim();
        String p = isBlank(path) ? "/health" : path.trim();
        if (base.endsWith("/") && p.startsWith("/")) {
            return base.substring(0, base.length() - 1) + p;
        }
        if (!base.endsWith("/") && !p.startsWith("/")) {
            return base + "/" + p;
        }
        return base + p;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
