package com.smartroad.srmp.agent.embedding.impl;

import com.smartroad.srmp.agent.embedding.EmbeddingClient;
import com.smartroad.srmp.agent.embedding.EmbeddingHealthResponse;
import com.smartroad.srmp.agent.embedding.EmbeddingHealthService;
import com.smartroad.srmp.agent.embedding.EmbeddingProperties;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class EmbeddingHealthServiceImpl implements EmbeddingHealthService {

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private EmbeddingProperties embeddingProperties;

    @Override
    public EmbeddingHealthResponse check() {
        long start = System.currentTimeMillis();
        EmbeddingHealthResponse response = new EmbeddingHealthResponse();
        response.setProvider(embeddingProperties == null ? null : embeddingProperties.getProvider());
        response.setEndpoint(maskEndpoint(embeddingProperties == null ? null : embeddingProperties.getEndpoint()));
        response.setModel(embeddingClient == null ? null : embeddingClient.model());
        response.setExpectedDimensions(embeddingClient == null ? null : embeddingClient.dimensions());

        try {
            List<Float> vector = embeddingClient.embed("道路养护 embedding health check");
            response.setActualDimensions(vector == null ? 0 : vector.size());
            response.setAvailable(vector != null && !vector.isEmpty());

            if (!Boolean.TRUE.equals(response.getAvailable())) {
                response.setSuggestion("Embedding 返回为空，请检查 provider、endpoint、api-key 和 model");
            } else if (response.getExpectedDimensions() != null
                    && response.getActualDimensions() != null
                    && !response.getExpectedDimensions().equals(response.getActualDimensions())) {
                response.setAvailable(false);
                response.setSuggestion("Embedding 返回维度与配置维度不一致，请检查 dimensions 或数据库 vector 维度");
            } else {
                response.setSuggestion("Embedding provider 可用");
            }
        } catch (Exception e) {
            response.setAvailable(false);
            response.setActualDimensions(0);
            response.setErrorType(rootCause(e).getClass().getSimpleName());
            response.setErrorMessage(rootCause(e).getMessage());
            response.setSuggestion("请检查网络代理、JDK TLS、API Key、endpoint、模型名称或服务返回格式");
        } finally {
            response.setCostMs(System.currentTimeMillis() - start);
        }

        return response;
    }

    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    private String maskEndpoint(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        return endpoint.trim();
    }
}
