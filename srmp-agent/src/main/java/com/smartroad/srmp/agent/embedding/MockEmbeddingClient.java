package com.smartroad.srmp.agent.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "srmp.ai.embedding", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingClient implements EmbeddingClient {

    @Resource
    private EmbeddingProperties properties;

    @Override
    public List<Float> embed(String text) {
        int dimensions = dimensions();
        float[] vector = new float[dimensions];
        String value = text == null ? "" : text;
        String[] tokens = value.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+");
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) continue;
            int index = Math.abs(hash(token)) % dimensions;
            vector[index] += 1.0f;
        }
        normalize(vector);
        List<Float> result = new ArrayList<>(dimensions);
        for (float v : vector) result.add(v);
        return result;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> result = new ArrayList<>();
        if (texts == null) return result;
        for (String text : texts) result.add(embed(text));
        return result;
    }

    @Override
    public String model() {
        return properties == null ? "mock-hash-embedding" : properties.getModel();
    }

    @Override
    public int dimensions() {
        return properties == null ? 1024 : properties.safeDimensions();
    }

    private int hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            int value = 0;
            for (int i = 0; i < 4 && i < bytes.length; i++) value = (value << 8) | (bytes[i] & 0xff);
            return value;
        } catch (Exception e) {
            return text.hashCode();
        }
    }

    private void normalize(float[] vector) {
        double sum = 0.0d;
        for (float v : vector) sum += v * v;
        double norm = Math.sqrt(sum);
        if (norm <= 0.0d) return;
        for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / norm);
    }
}
