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
@ConditionalOnProperty(prefix = "srmp.ai.embedding", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

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
        String url = endpoint();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", texts == null ? Collections.emptyList() : texts);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getApiKey() != null && !properties.getApiKey().trim().isEmpty()) headers.setBearerAuth(properties.getApiKey());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return parseOpenAiResponse(response.getBody());
    }

    @Override
    public String model() {
        return properties == null ? "text-embedding-3-small" : properties.getModel();
    }

    @Override
    public int dimensions() {
        return properties == null ? 1536 : properties.safeDimensions();
    }

    private String endpoint() {
        if (properties == null || properties.getEndpoint() == null || properties.getEndpoint().trim().isEmpty()) {
            return "https://api.openai.com/v1/embeddings";
        }
        String endpoint = properties.getEndpoint().trim();
        if (endpoint.endsWith("/embeddings")) return endpoint;
        if (endpoint.endsWith("/")) return endpoint + "v1/embeddings";
        return endpoint + "/v1/embeddings";
    }

    private List<List<Float>> parseOpenAiResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) throw new IllegalStateException("embedding response missing data array");
            List<List<Float>> result = new ArrayList<>();
            for (JsonNode item : data) result.add(toFloatList(item.get("embedding")));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("parse openai-compatible embedding response failed: " + e.getMessage(), e);
        }
    }

    private List<Float> toFloatList(JsonNode node) {
        List<Float> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode n : node) result.add((float) n.asDouble());
        return result;
    }
}
