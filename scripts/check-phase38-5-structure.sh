#!/usr/bin/env bash
set -euo pipefail

echo "==> Phase38.5 结构检查"

grep -q "default boolean enabled()" srmp-agent/src/main/java/com/smartroad/srmp/agent/llm/LlmClient.java   || { echo "[FAIL] LlmClient 缺少 enabled()"; exit 1; }

grep -q "default Map<String, Object> diagnostics()" srmp-agent/src/main/java/com/smartroad/srmp/agent/llm/LlmClient.java   || { echo "[FAIL] LlmClient diagnostics 泛型不完整"; exit 1; }

grep -q "rawResponsePreview" srmp-agent/src/main/java/com/smartroad/srmp/agent/llm/OpenAiCompatibleLlmClient.java   || { echo "[FAIL] OpenAiCompatibleLlmClient 缺少 rawResponsePreview 诊断"; exit 1; }

grep -q "curlFallback" srmp-agent/src/main/java/com/smartroad/srmp/agent/llm/OpenAiCompatibleLlmClient.java   || { echo "[FAIL] OpenAiCompatibleLlmClient 缺少 curl fallback"; exit 1; }

grep -q "mapAiToolPlanner.plan" srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java   || { echo "[FAIL] MapAiAgentServiceImpl 未使用 MapAiToolPlanner"; exit 1; }

grep -q "mapAiAnswerEnhancerRegistry.enhance" srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java   || { echo "[FAIL] MapAiAgentServiceImpl 未使用 MapAiAnswerEnhancerRegistry"; exit 1; }

if grep -q "private List planTools" srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java; then
  echo "[FAIL] MapAiAgentServiceImpl 仍存在旧 planTools"
  exit 1
fi

echo "[OK] Phase38.5 结构检查通过"
