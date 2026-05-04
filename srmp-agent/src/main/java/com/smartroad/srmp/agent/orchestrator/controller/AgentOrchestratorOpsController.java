package com.smartroad.srmp.agent.orchestrator.controller;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestrator;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorProperties;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorRouter;
import com.smartroad.srmp.agent.tool.AiToolRegistry;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase50.5：LangGraph 编排观测与灰度控制台接口。
 *
 * 目标：不用直接登录 Python 容器，也能从 Java 后端看到当前编排配置、
 * LangGraph Runtime 最近调用、失败/回退情况，并触发一条只读 smoke 请求。
 */
@RestController
@RequestMapping("/api/agent/orchestrator/ops")
public class AgentOrchestratorOpsController {

    @Resource
    private AgentOrchestratorProperties properties;

    @Resource
    private List<AgentOrchestrator> orchestrators;

    @Resource
    private AiToolRegistry aiToolRegistry;

    @Resource
    private AgentOrchestratorRouter router;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(value = "recentLimit", required = false, defaultValue = "10") Integer recentLimit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("provider", properties.getProvider());
        data.put("fallbackToNative", properties.getFallbackToNative());
        data.put("allowWriteTools", properties.getAllowWriteTools());
        data.put("availableProviders", providerNames());
        data.put("localToolCount", aiToolRegistry.list().size());
        data.put("langgraphUrl", properties.getLanggraphUrl());
        data.put("langgraphEndpointPath", properties.getLanggraphEndpointPath());
        data.put("langgraphReady", remoteGet(properties.getLanggraphReadyPath()));
        data.put("runtimeSummary", remoteGet("/api/srmp/langgraph/observability/summary"));
        data.put("runtimeRecent", remoteGet("/api/srmp/langgraph/observability/recent?limit=" + safeLimit(recentLimit)));
        data.put("toolGatewayDebug", remoteGet("/api/srmp/langgraph/debug/tool-gateway"));
        return R.ok(data);
    }

    @GetMapping("/recent")
    public R<Object> recent(@RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                            @RequestParam(value = "status", required = false) String status) {
        String path = "/api/srmp/langgraph/observability/recent?limit=" + safeLimit(limit);
        if (!isBlank(status)) {
            path = path + "&status=" + status.trim();
        }
        return R.ok(remoteGet(path));
    }

    @PostMapping("/smoke")
    public R<Map<String, Object>> smoke(@RequestBody(required = false) Map<String, Object> body) {
        MapAiAgentRequest request = new MapAiAgentRequest();
        request.setMessage(stringValue(body, "message", "Phase50.5 LangGraph 编排 smoke：请基于 G210 和知识库给出道路养护建议摘要"));

        MapAiContext context = new MapAiContext();
        context.setTenantId(stringValue(body, "tenantId", resolveTenantId()));
        context.setMode(stringValue(body, "mode", "ROUTE"));
        context.setRouteCode(stringValue(body, "routeCode", "G210"));
        context.setYear(intValue(body, "year", 2026));
        context.setUserQuestion(request.getMessage());
        request.setMapContext(context);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("traceId", stringValue(body, "traceId", "phase50-5-" + UUID.randomUUID().toString().replace("-", "")));
        options.put("smoke", true);
        options.put("readOnly", true);
        request.setOptions(options);

        long start = System.currentTimeMillis();
        MapAiAgentResponse response = router.chat(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("request", request);
        result.put("response", response);
        result.put("provider", response != null && response.getData() != null ? response.getData().get("orchestratorProvider") : null);
        result.put("fallback", response != null && response.getData() != null ? response.getData().get("orchestratorFallback") : null);
        result.put("runtimeSummary", remoteGet("/api/srmp/langgraph/observability/summary"));
        return R.ok(result);
    }

    private List<String> providerNames() {
        List<String> list = new ArrayList<>();
        if (orchestrators != null) {
            for (AgentOrchestrator orchestrator : orchestrators) {
                if (orchestrator != null && orchestrator.provider() != null) {
                    list.add(orchestrator.provider());
                }
            }
        }
        return list;
    }

    private Map<String, Object> remoteGet(String path) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate();
            ResponseEntity<Object> entity = restTemplate.getForEntity(url, Object.class);
            data.put("ok", entity.getStatusCode().is2xxSuccessful());
            data.put("httpStatus", entity.getStatusCodeValue());
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
        factory.setReadTimeout(8000);
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

    private int safeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        if (limit < 1) {
            return 1;
        }
        if (limit > 100) {
            return 100;
        }
        return limit;
    }

    private String resolveTenantId() {
        String tenantId = TenantContextHolder.getTenantId();
        return isBlank(tenantId) ? "default" : tenantId;
    }

    private String stringValue(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || map.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(map.get(key));
        return isBlank(value) ? defaultValue : value.trim();
    }

    private Integer intValue(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || map.get(key) == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(String.valueOf(map.get(key)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
