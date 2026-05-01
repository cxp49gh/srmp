#!/usr/bin/env bash
set -euo pipefail

echo "==> 检查 Phase37.5 策略结构"

required_files=(
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhanceContext.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhancer.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhancerRegistry.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlanner.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlannerImpl.java"
)

for f in "${required_files[@]}"; do
  if [ ! -f "$f" ]; then
    echo "[FAIL] 缺少文件：$f"
    exit 1
  fi
done

grep -q "mapAiToolPlanner.plan" srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java \
  || { echo "[FAIL] MapAiAgentServiceImpl 未接入 MapAiToolPlanner"; exit 1; }

grep -q "mapAiAnswerEnhancerRegistry.enhance" srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java \
  || { echo "[FAIL] MapAiAgentServiceImpl 未接入 MapAiAnswerEnhancerRegistry"; exit 1; }

grep -q "基于本次分析生成方案草稿" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue \
  || { echo "[FAIL] AgentChatFloat 未接入回答下方生成方案草稿按钮"; exit 1; }

echo "[OK] Phase37.5 策略结构检查通过"
