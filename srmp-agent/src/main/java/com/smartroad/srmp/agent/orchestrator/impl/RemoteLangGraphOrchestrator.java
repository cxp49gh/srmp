package com.smartroad.srmp.agent.orchestrator.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestrator;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorProperties;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase50.4：远程 LangGraph 编排器增强。
 *
 * 关键增强：
 * 1. 透传 X-Tenant-Id / X-AI-Trace-Id，便于 LangGraph 再调用 Java Tool Gateway；
 * 2. 支持直接返回 MapAiAgentResponse 或 R 包装响应；
 * 3. 对空答案、非 2xx、失败 code 做显式异常，交给 Router 自动 fallback native；
 * 4. 标准化 data/trace/sources 字段，前端和 Trace 抽屉不用猜字段。
 */
@Slf4j
@Service
public class RemoteLangGraphOrchestrator implements AgentOrchestrator {

    @Resource
    private AgentOrchestratorProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String provider() {
        return "langgraph";
    }

    @Override
    public MapAiAgentResponse chat(MapAiAgentRequest request) {
        MapAiAgentRequest safeRequest = request == null ? new MapAiAgentRequest() : request;
        String url = buildUrl(properties.getLanggraphUrl(), properties.getLanggraphEndpointPath());
        RestTemplate restTemplate = buildRestTemplate(defaultInt(properties.getConnectTimeoutMs(), 10000),
                defaultInt(properties.getReadTimeoutMs(), 300000));

        String tenantId = resolveTenantId(safeRequest);
        String traceId = resolveTraceId(safeRequest);
        HttpHeaders headers = buildHeaders(tenantId, traceId);
        HttpEntity<MapAiAgentRequest> httpEntity = new HttpEntity<>(safeRequest, headers);

        long start = System.currentTimeMillis();
        ResponseEntity<Map> entity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Map.class);
        if (!entity.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("LangGraph 编排服务 HTTP 状态异常：" + entity.getStatusCodeValue());
        }

        Map body = entity.getBody();
        if (body == null) {
            throw new IllegalStateException("LangGraph 编排服务返回为空");
        }

        Object responseBody = unwrapResponseBody(body);
        MapAiAgentResponse response = objectMapper.convertValue(responseBody, MapAiAgentResponse.class);
        normalizeResponse(response, url, tenantId, traceId, System.currentTimeMillis() - start);
        return response;
    }

    public MapAgentRunResponse run(MapAgentRunRequest request) {
        MapAgentRunRequest safeRequest = request == null ? new MapAgentRunRequest() : request;
        String url = buildUrl(properties.getLanggraphUrl(), properties.getLanggraphEndpointPath());
        RestTemplate restTemplate = buildRestTemplate(defaultInt(properties.getConnectTimeoutMs(), 10000),
                defaultInt(properties.getReadTimeoutMs(), 300000));

        String tenantId = resolveTenantId(safeRequest);
        String traceId = resolveTraceId(safeRequest);
        HttpHeaders headers = buildHeaders(tenantId, traceId);
        HttpEntity<MapAgentRunRequest> httpEntity = new HttpEntity<>(safeRequest, headers);

        long start = System.currentTimeMillis();
        ResponseEntity<Map> entity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Map.class);
        if (!entity.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("LangGraph 编排服务 HTTP 状态异常：" + entity.getStatusCodeValue());
        }

        Map body = entity.getBody();
        if (body == null) {
            throw new IllegalStateException("LangGraph 编排服务返回为空");
        }

        Object responseBody = unwrapResponseBody(body);
        MapAgentRunResponse response = objectMapper.convertValue(responseBody, MapAgentRunResponse.class);
        normalizeRunResponse(response, url, tenantId, traceId, System.currentTimeMillis() - start);
        return response;
    }

    private Object unwrapResponseBody(Map body) {
        if (body.containsKey("data") && (body.containsKey("code") || body.containsKey("message"))) {
            Object code = body.get("code");
            if (code != null && !"0".equals(String.valueOf(code))) {
                throw new IllegalStateException("LangGraph 编排服务返回失败：" + body.get("message"));
            }
            Object data = body.get("data");
            if (data == null) {
                throw new IllegalStateException("LangGraph 编排服务 data 为空");
            }
            return data;
        }
        return body;
    }

    private void normalizeResponse(MapAiAgentResponse response, String url, String tenantId, String traceId, long costMs) {
        if (response == null) {
            throw new IllegalStateException("LangGraph 编排服务响应无法转换为 MapAiAgentResponse");
        }
        if (isBlank(response.getAnswer())) {
            throw new IllegalStateException("LangGraph 编排服务返回空答案");
        }
        if (isBlank(response.getMode())) {
            response.setMode("LANGGRAPH_AGENT");
        }
        if (response.getSources() == null && response.getKnowledgeSources() != null) {
            response.setSources(response.getKnowledgeSources());
        }
        if (response.getKnowledgeSources() == null && response.getSources() != null) {
            response.setKnowledgeSources(response.getSources());
        }
        Map<String, Object> data = response.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            response.setData(data);
        }
        data.put("orchestratorProvider", "langgraph");
        data.put("orchestratorFallback", false);
        data.put("remoteLangGraphUrl", url);
        data.put("remoteCostMs", costMs);
        data.put("tenantId", tenantId);
        data.put("traceId", traceId);

        Map<String, Object> trace = response.getTrace();
        if (trace == null) {
            trace = new LinkedHashMap<>();
            response.setTrace(trace);
        }
        trace.put("traceId", traceId);
        trace.put("mode", response.getMode());
        trace.put("orchestratorProvider", "langgraph");
        trace.put("orchestratorFallback", false);
        trace.put("remoteCostMs", costMs);
    }

    private void normalizeRunResponse(MapAgentRunResponse response, String url, String tenantId, String traceId, long costMs) {
        if (response == null) {
            throw new IllegalStateException("LangGraph 编排服务响应无法转换为 MapAgentRunResponse");
        }
        if (isBlank(response.getAnswer())) {
            response.setAnswer("LangGraph 未返回回答正文，请查看 AI 执行过程。");
        }
        if (isBlank(response.getMode())) {
            response.setMode("LANGGRAPH_MAP_AGENT");
        }
        if (response.getData() == null) {
            response.setData(new LinkedHashMap<String, Object>());
        }
        response.getData().put("orchestratorProvider", "langgraph");
        response.getData().put("orchestratorFallback", false);
        response.getData().put("remoteLangGraphUrl", url);
        response.getData().put("remoteCostMs", costMs);
        response.getData().put("tenantId", tenantId);
        response.getData().put("traceId", traceId);
        if (response.getTrace() == null) {
            response.setTrace(new LinkedHashMap<String, Object>());
        }
        response.getTrace().put("traceId", traceId);
        response.getTrace().put("mode", response.getMode());
        response.getTrace().put("orchestratorProvider", "langgraph");
        response.getTrace().put("orchestratorFallback", false);
        response.getTrace().put("remoteCostMs", costMs);
    }

    private HttpHeaders buildHeaders(String tenantId, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        if (!isBlank(tenantId)) {
            headers.set("X-Tenant-Id", tenantId);
        }
        if (!isBlank(traceId)) {
            headers.set("X-AI-Trace-Id", traceId);
        }
        return headers;
    }

    private RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private String resolveTenantId(MapAiAgentRequest request) {
        MapAiContext context = request.getMapContext();
        String tenantId = context == null ? null : context.getTenantId();
        if (isBlank(tenantId)) {
            tenantId = TenantContextHolder.getTenantId();
        }
        return isBlank(tenantId) ? "default" : tenantId.trim();
    }

    private String resolveTraceId(MapAiAgentRequest request) {
        if (request.getOptions() != null) {
            Object optionTraceId = request.getOptions().get("traceId");
            if (optionTraceId != null && !isBlank(String.valueOf(optionTraceId))) {
                return String.valueOf(optionTraceId).trim();
            }
        }
        return "lg-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveTenantId(MapAgentRunRequest request) {
        MapAiContext context = request.getMapContext();
        String tenantId = context == null ? null : context.getTenantId();
        if (isBlank(tenantId)) {
            tenantId = TenantContextHolder.getTenantId();
        }
        return isBlank(tenantId) ? "default" : tenantId.trim();
    }

    private String resolveTraceId(MapAgentRunRequest request) {
        if (request.getOptions() != null) {
            Object optionTraceId = request.getOptions().get("traceId");
            if (optionTraceId != null && !isBlank(String.valueOf(optionTraceId))) {
                return String.valueOf(optionTraceId).trim();
            }
        }
        return "java-lg-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildUrl(String baseUrl, String path) {
        String base = isBlank(baseUrl) ? "http://127.0.0.1:18080" : baseUrl.trim();
        String p = isBlank(path) ? "/api/srmp/langgraph/map-agent/chat" : path.trim();
        if (base.endsWith("/") && p.startsWith("/")) {
            return base.substring(0, base.length() - 1) + p;
        }
        if (!base.endsWith("/") && !p.startsWith("/")) {
            return base + "/" + p;
        }
        return base + p;
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
