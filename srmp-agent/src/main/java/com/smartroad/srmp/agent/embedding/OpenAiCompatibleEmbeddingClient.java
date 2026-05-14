package com.smartroad.srmp.agent.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "srmp.ai.embedding", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    @Resource
    private EmbeddingProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> batch = embedBatch(Collections.singletonList(text == null ? "" : text));
        return batch.isEmpty() ? Collections.emptyList() : batch.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        try {
            return doEmbedBatch(texts);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("handshake") || msg.contains("SSL") || msg.contains("TLS"))) {
                log.warn("HttpURLConnection failed with TLS error, trying curl fallback: {}", msg);
                try {
                    return curlFallback(texts);
                } catch (Exception curlEx) {
                    throw new RuntimeException("curl fallback also failed: " + curlEx.getMessage(), curlEx);
                }
            }
            throw new RuntimeException("openai-compatible embedding request failed: " + msg, e);
        }
    }

    private List<List<Float>> doEmbedBatch(List<String> texts) throws Exception {
        String urlStr = endpoint();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "application/json");
        String apiKey = properties != null ? properties.getApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", texts == null ? Collections.emptyList() : texts);
        if (dimensions() > 0) {
            body.put("dimensions", dimensions());
        }
        String json = objectMapper.writeValueAsString(body);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();
        if (code >= 400) {
            throw new IllegalStateException("embedding http " + code + ": " + responseMessage(sb.toString()));
        }
        return parseOpenAiResponse(sb.toString());
    }

    private List<List<Float>> curlFallback(List<String> texts) throws Exception {
        String urlStr = endpoint();
        String apiKey = properties != null ? properties.getApiKey() : null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", texts == null ? Collections.emptyList() : texts);
        if (dimensions() > 0) {
            body.put("dimensions", dimensions());
        }
        String json = objectMapper.writeValueAsString(body);

        List<String> cmd = new ArrayList<>();
        cmd.add("curl");
        cmd.add("-s");
        cmd.add("-X");
        cmd.add("POST");
        cmd.add(urlStr);
        cmd.add("-H");
        cmd.add("Content-Type: application/json");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            cmd.add("-H");
            cmd.add("Authorization: Bearer " + apiKey);
        }
        cmd.add("-d");
        cmd.add(json);
        cmd.add("--max-time");
        cmd.add("30");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) output.append(line);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("curl exited with " + exitCode + ": " + output);
        }
        return parseOpenAiResponse(output.toString());
    }

    @Override
    public String model() {
        return properties == null ? "text-embedding-3-small" : properties.getModel();
    }

    @Override
    public int dimensions() {
        return properties == null ? 1024 : properties.safeDimensions();
    }

    private String endpoint() {
        if (properties == null || properties.getEndpoint() == null || properties.getEndpoint().trim().isEmpty()) {
            return "https://api.openai.com/v1/embeddings";
        }
        String endpoint = properties.getEndpoint().trim();
        if (endpoint.endsWith("/embeddings")) return endpoint;
        if (endpoint.endsWith("/api/embed") || endpoint.endsWith("/api/embeddings")) return endpoint;
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (endpoint.endsWith("/v1")) return endpoint + "/embeddings";
        return endpoint + "/v1/embeddings";
    }

    private List<List<Float>> parseOpenAiResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                List<List<Float>> compatible = parseCompatibleEmbeddingArrays(root);
                if (!compatible.isEmpty()) {
                    return compatible;
                }
                throw new IllegalStateException(responseMessage(json));
            }
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

    private List<List<Float>> parseCompatibleEmbeddingArrays(JsonNode root) {
        JsonNode arr = firstArray(root, "embeddings", "embedding", "vectors");
        if (arr == null) {
            return Collections.emptyList();
        }
        if (arr.size() > 0 && arr.get(0).isNumber()) {
            return Collections.singletonList(toFloatList(arr));
        }
        List<List<Float>> result = new ArrayList<>();
        for (JsonNode item : arr) {
            result.add(toFloatList(item));
        }
        return result;
    }

    private JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            if (root.has(name) && root.get(name).isArray()) {
                return root.get(name);
            }
        }
        return null;
    }

    private String responseMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            if (root.has("error")) return root.get("error").toString();
            if (root.has("message")) return root.get("message").asText();
            if (root.has("msg")) return root.get("msg").asText();
            if (root.has("code")) return "code=" + root.get("code").asText() + ", raw=" + preview(json);
            return "embedding response missing data array, raw=" + preview(json);
        } catch (Exception e) {
            return "embedding response parse failed, raw=" + preview(json);
        }
    }

    private String preview(String text) {
        if (text == null) return "";
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
