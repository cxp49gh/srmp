package com.smartroad.srmp.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
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
public class OpenAiCompatibleLlmClient implements LlmClient {

    @Resource
    private LlmProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!properties.enabled()) {
            return null;
        }

        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return null;
        }

        String url = baseUrl.endsWith("/")
                ? baseUrl + "chat/completions"
                : baseUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.put("messages", messages);

        try {
            return doChat(url, body);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("handshake") || msg.contains("SSL") || msg.contains("TLS"))) {
                log.warn("HttpURLConnection failed with TLS error, trying curl fallback: {}", msg);
                try {
                    return curlFallback(url, body);
                } catch (Exception curlEx) {
                    log.warn("curl fallback also failed: {}", curlEx.getMessage());
                    return null;
                }
            }
            log.warn("LLM request failed: {}", e.getMessage());
            return null;
        }
    }

    private String doChat(String urlStr, Map<String, Object> body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json");
        String apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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
        return parseResponse(sb.toString());
    }

    private String curlFallback(String urlStr, Map<String, Object> body) throws Exception {
        String apiKey = properties.getApiKey();
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
        cmd.add("60");

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
        return parseResponse(output.toString());
    }

    private String parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? null : content.asText();
        } catch (Exception e) {
            throw new RuntimeException("parse LLM response failed: " + e.getMessage(), e);
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content == null ? "" : content);
        return item;
    }
}
