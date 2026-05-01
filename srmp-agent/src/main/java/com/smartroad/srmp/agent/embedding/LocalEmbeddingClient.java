package com.smartroad.srmp.agent.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "srmp.ai.embedding", name = "provider", havingValue = "local")
public class LocalEmbeddingClient implements EmbeddingClient {

    @Resource
    private EmbeddingProperties properties;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> batch = embedBatch(Collections.singletonList(text == null ? "" : text));
        return batch.isEmpty() ? Collections.emptyList() : batch.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (properties == null || properties.getEndpoint() == null || properties.getEndpoint().trim().isEmpty()) {
            throw new IllegalStateException("srmp.ai.embedding.endpoint is required when provider=local");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", texts == null ? Collections.emptyList() : texts);
        body.put("texts", texts == null ? Collections.emptyList() : texts);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(properties.getEndpoint(), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return parseEmbeddings(response.getBody());
    }

    @Override
    public String model() {
        return properties == null ? "local-embedding" : properties.getModel();
    }

    @Override
    public int dimensions() {
        return properties == null ? 1024 : properties.safeDimensions();
    }

    private List<List<Float>> parseEmbeddings(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode arr = firstArray(root, "embeddings", "embedding", "vectors");
            if (arr == null && root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isArray() && data.size() > 0 && data.get(0).has("embedding")) {
                    List<List<Float>> result = new ArrayList<>();
                    for (JsonNode item : data) result.add(toFloatList(item.get("embedding")));
                    return result;
                }
            }
            if (arr == null) throw new IllegalStateException("embedding response does not contain embeddings/data[].embedding");
            if (arr.isArray() && arr.size() > 0 && arr.get(0).isNumber()) {
                return Collections.singletonList(toFloatList(arr));
            }
            List<List<Float>> result = new ArrayList<>();
            for (JsonNode item : arr) result.add(toFloatList(item));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("parse local embedding response failed: " + e.getMessage(), e);
        }
    }

    private JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) if (root.has(name) && root.get(name).isArray()) return root.get(name);
        return null;
    }

    private List<Float> toFloatList(JsonNode node) {
        List<Float> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode n : node) result.add((float) n.asDouble());
        return result;
    }
}
