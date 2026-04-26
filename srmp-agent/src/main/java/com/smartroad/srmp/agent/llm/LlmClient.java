package com.smartroad.srmp.agent.llm;

public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);
}
