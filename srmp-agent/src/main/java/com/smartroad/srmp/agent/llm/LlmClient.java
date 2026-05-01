package com.smartroad.srmp.agent.llm;

import java.util.Collections;
import java.util.Map;

public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);

    default boolean enabled() {
        return true;
    }

    default Map<String, Object> diagnostics() {
        return Collections.emptyMap();
    }
}
