package com.smartroad.srmp.agent.llm;

import java.util.Collections;
import java.util.Map;

/**
 * Phase38.5：LLM 客户端统一契约。
 */
public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);

    default boolean enabled() {
        return true;
    }

    default Map<String, Object> diagnostics() {
        return Collections.emptyMap();
    }
}
