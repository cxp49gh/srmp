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

/**
 * Phase38.5：OpenAI-compatible LLM client 工程化收口。
 *
 * 保留 HttpURLConnection + Proxy.NO_PROXY + curl fallback，
 * 同时补齐 diagnostics，避免 MapAgent 大 prompt 空响应时无法定位。
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    @Resource
    private LlmProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ThreadLocal<Map<String, Object>> lastDiagnostics = ThreadLocal.withInitial(LinkedHashMap::new);

    @Override
    public boolean enabled() {
        return properties != null && properties.enabled();
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", properties == null ? null : properties.getProvider());
        data.put("model", properties == null ? null : properties.getModel());
        data.put("baseUrl", properties == null ? null : properties.getBaseUrl());
        data.put("enabled", enabled());
        data.putAll(lastDiagnostics.get());
        return data;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> diag = baseDiagnostics(systemPrompt, userPrompt);
        long start = System.currentTimeMillis();

        try {
            if (properties == null || !properties.enabled()) {
                diag.put("status", "DISABLED");
                diag.put("errorMessage", "srmp.llm 未启用或 api-key 为占位值");
                return finish(null, diag, start);
            }

            String baseUrl = properties.getBaseUrl();
            if (isBlank(baseUrl)) {
                diag.put("status", "BASE_URL_EMPTY");
                diag.put("errorMessage", "srmp.llm.base-url 未配置");
                return finish(null, diag, start);
            }

            String apiKey = properties.getApiKey();
            if (isBlank(apiKey)) {
                diag.put("status", "API_KEY_EMPTY");
                diag.put("errorMessage", "srmp.llm.api-key 未配置");
                return finish(null, diag, start);
            }

            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            Map<String, Object> body = buildBody(systemPrompt, userPrompt);

            String raw;
            try {
                raw = doChat(url, body);
                diag.put("transport", "httpurlconnection");
            } catch (Exception e) {
                Throwable root = rootCause(e);
                String msg = root.getMessage();
                if (msg != null && (msg.contains("handshake") || msg.contains("SSL") || msg.contains("TLS"))) {
                    log.warn("HttpURLConnection failed with TLS error, trying curl fallback: {}", msg);
                    diag.put("firstTransportError", root.getClass().getSimpleName() + ": " + msg);
                    raw = curlFallback(url, body);
                    diag.put("transport", "curl");
                } else {
                    throw e;
                }
            }

            diag.put("rawResponsePreview", shortText(raw, 2000));
            String answer = parseResponse(raw, diag);
            if (isBlank(answer)) {
                diag.put("status", "EMPTY_CONTENT");
                diag.put("errorMessage", firstNonBlank(
                        String.valueOf(diag.getOrDefault("errorMessage", "")),
                        "LLM choices[0].message.content is empty"
                ));
                return finish(null, diag, start);
            }

            diag.put("status", "SUCCESS");
            diag.put("success", true);
            diag.put("answerChars", answer.length());
            return finish(answer, diag, start);
        } catch (Exception e) {
            Throwable root = rootCause(e);
            diag.put("status", "ERROR");
            diag.put("errorType", root.getClass().getSimpleName());
            diag.put("errorMessage", root.getMessage());
            log.warn("LLM request failed: {}", root.getMessage());
            return finish(null, diag, start);
        }
    }

    private Map<String, Object> baseDiagnostics(String systemPrompt, String userPrompt) {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("provider", properties == null ? null : properties.getProvider());
        diag.put("model", properties == null ? null : properties.getModel());
        diag.put("baseUrl", properties == null ? null : properties.getBaseUrl());
        diag.put("enabled", enabled());
        diag.put("success", false);
        diag.put("status", "INIT");
        diag.put("errorType", null);
        diag.put("errorMessage", null);
        diag.put("systemPromptChars", length(systemPrompt));
        diag.put("userPromptChars", length(userPrompt));
        diag.put("promptChars", length(systemPrompt) + length(userPrompt));
        return diag;
    }

    private Map<String, Object> buildBody(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 1800);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.put("messages", messages);
        return body;
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
        if (!isBlank(apiKey)) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        String json = objectMapper.writeValueAsString(body);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        conn.disconnect();

        if (code >= 400) {
            throw new RuntimeException("LLM HTTP " + code + ": " + shortText(sb.toString(), 1000));
        }
        return sb.toString();
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
        if (!isBlank(apiKey)) {
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
            while ((line = br.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("curl exited with " + exitCode + ": " + shortText(output.toString(), 1000));
        }
        return output.toString();
    }

    private String parseResponse(String json, Map<String, Object> diag) {
        try {
            JsonNode root = objectMapper.readTree(isBlank(json) ? "{}" : json);
            diag.put("responseId", root.path("id").asText(""));

            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull() && error.size() > 0) {
                diag.put("status", "RESPONSE_ERROR");
                diag.put("errorType", error.path("type").asText(""));
                diag.put("errorMessage", firstNonBlank(error.path("message").asText(""), error.toString()));
                return null;
            }

            JsonNode choice = root.path("choices").path(0);
            diag.put("finishReason", choice.path("finish_reason").asText(""));
            diag.put("choicePreview", shortText(choice.toString(), 2000));

            JsonNode message = choice.path("message");
            String answer = extractContent(message);
            if (isBlank(answer)) {
                diag.put("errorMessage", buildEmptyContentMessage(choice));
                return null;
            }
            return answer;
        } catch (Exception e) {
            throw new RuntimeException("parse LLM response failed: " + e.getMessage(), e);
        }
    }

    private String extractContent(JsonNode message) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return "";
        }

        String value = extractText(message.path("content"));
        if (!isBlank(value)) {
            return value;
        }

        value = message.path("text").asText("");
        if (!isBlank(value)) {
            return value;
        }

        value = message.path("reasoning_content").asText("");
        if (!isBlank(value)) {
            return value;
        }

        return "";
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                String text = item.path("text").asText("");
                if (isBlank(text)) {
                    text = item.path("content").asText("");
                }
                if (!isBlank(text)) {
                    if (sb.length() > 0) {
                        sb.append("\\n");
                    }
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        if (node.isObject()) {
            String text = node.path("text").asText("");
            if (!isBlank(text)) {
                return text;
            }
            text = node.path("content").asText("");
            if (!isBlank(text)) {
                return text;
            }
        }
        return "";
    }

    private String buildEmptyContentMessage(JsonNode choice) {
        String finishReason = choice.path("finish_reason").asText("");
        JsonNode message = choice.path("message");
        String refusal = message.path("refusal").asText("");
        String reason = "LLM choices[0].message.content is empty";
        if (!isBlank(finishReason)) {
            reason += ", finish_reason=" + finishReason;
        }
        if (!isBlank(refusal)) {
            reason += ", refusal=" + refusal;
        }
        return reason;
    }

    private String finish(String answer, Map<String, Object> diag, long start) {
        diag.put("costMs", System.currentTimeMillis() - start);
        lastDiagnostics.set(diag);
        return answer;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content == null ? "" : content);
        return item;
    }

    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
