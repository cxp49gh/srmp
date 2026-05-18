package com.smartroad.srmp.agent.outline.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroad.srmp.agent.outline.config.OutlineProperties;
import com.smartroad.srmp.agent.outline.service.OutlineService;
import com.smartroad.srmp.agent.outline.vo.OutlineSearchResult;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;

@Service
public class OutlineServiceImpl implements OutlineService {

    @Resource
    private OutlineProperties properties;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map status() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean enabled = Boolean.TRUE.equals(properties.getEnabled());
        boolean usable = properties.usable();
        boolean syncEnabled = Boolean.TRUE.equals(properties.getSyncEnabled());
        result.put("enabled", enabled);
        result.put("usable", usable);
        result.put("baseUrl", properties.getBaseUrl());
        result.put("syncEnabled", syncEnabled);
        result.put("defaultCollectionId", properties.getDefaultCollectionId());
        if (!enabled) {
            result.put("message", "Outline 功能未启用，请联系管理员开启");
        } else if (!usable) {
            result.put("message", "Outline 配置不完整或不可访问，请检查 baseUrl、Token、网络");
        } else if (!syncEnabled) {
            result.put("message", "当前只允许在线搜索，不允许同步入库");
        }
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("hasBaseUrl", notBlank(properties.getBaseUrl()));
        diagnostics.put("hasApiToken", notBlank(properties.getApiToken()));
        diagnostics.put("hasDefaultCollection", notBlank(properties.getDefaultCollectionId()));
        result.put("diagnostics", diagnostics);
        return result;
    }

    private boolean notBlank(String value) {
        return value != null && value.trim().length() > 0;
    }

    @Override
    public List<OutlineSearchResult> search(String query, Integer limit) {
        if (!properties.usable() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        int size = limit == null ? properties.getSearchLimit() : limit;
        if (size <= 0) {
            size = 5;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("limit", size);
        if (properties.getDefaultCollectionId() != null && properties.getDefaultCollectionId().trim().length() > 0) {
            body.put("collectionId", properties.getDefaultCollectionId());
        }

        JsonNode root = post("/api/documents.search", body);
        List<OutlineSearchResult> list = new ArrayList<>();
        JsonNode data = root == null ? null : root.path("data");
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                OutlineSearchResult result = new OutlineSearchResult();
                JsonNode document = item.has("document") ? item.path("document") : item;
                result.setId(document.path("id").asText(null));
                result.setTitle(document.path("title").asText(""));
                result.setText(firstText(item, document));
                result.setUrl(outlineUrl(document.path("url").asText(null)));
                result.setScore(item.path("ranking").asDouble(1.0));
                list.add(result);
            }
        }
        return list;
    }

    @Override
    public Map document(String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.usable() || id == null || id.trim().isEmpty()) {
            result.put("enabled", properties.getEnabled());
            result.put("usable", properties.usable());
            result.put("message", "Outline 未启用或文档 ID 为空");
            return result;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        JsonNode root = post("/api/documents.info", body);
        JsonNode data = root == null ? null : root.path("data");

        if (data == null || data.isMissingNode()) {
            result.put("message", "未获取到 Outline 文档");
            return result;
        }

        result.put("id", data.path("id").asText());
        result.put("title", data.path("title").asText());
        result.put("text", data.path("text").asText());
        result.put("url", outlineUrl(data.path("url").asText(null)));
        return result;
    }

    private String outlineUrl(String rawUrl) {
        if (!notBlank(rawUrl)) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String baseUrl = properties == null ? "" : properties.getBaseUrl();
        if (!notBlank(baseUrl)) {
            return url;
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        return url.startsWith("/") ? base + url : base + "/" + url;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String baseUrl = properties.getBaseUrl();
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiToken());

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstText(JsonNode item, JsonNode document) {
        String text = item.path("context").asText(null);
        if (text == null || text.trim().isEmpty()) {
            text = item.path("text").asText(null);
        }
        if (text == null || text.trim().isEmpty()) {
            text = document.path("text").asText("");
        }
        return text;
    }
}
