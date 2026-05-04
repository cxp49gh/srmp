#!/usr/bin/env bash
set -euo pipefail

JAVA_BASE_URL="${JAVA_BASE_URL:-http://127.0.0.1:8080}"
LANGGRAPH_URL="${LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"
REQUIRE_LANGGRAPH="${REQUIRE_LANGGRAPH:-false}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

require_contains() {
  local text="$1"
  local pattern="$2"
  local message="$3"
  if ! printf '%s' "$text" | grep -q "$pattern"; then
    echo "$text" >&2
    fail "$message"
  fi
}

curl_json() {
  curl -fsS "$@"
}

PAYLOAD='{
  "message": "G210 中度裂缝怎么处理？请结合当前地图对象和知识库给出处置建议。",
  "mapContext": {
    "tenantId": "'"${TENANT_ID}"'",
    "mode": "OBJECT",
    "routeCode": "G210",
    "year": 2026,
    "mapObject": {
      "objectType": "DISEASE_RECORD",
      "diseaseName": "横向裂缝",
      "severity": "中度",
      "routeCode": "G210"
    }
  },
  "options": {
    "topK": 3,
    "traceId": "phase50-4-e2e"
  }
}'

echo "== 1. Java Tool Gateway list =="
JAVA_TOOLS=$(curl_json "${JAVA_BASE_URL}/api/agent/tools") || fail "Java /api/agent/tools 不可访问"
require_contains "$JAVA_TOOLS" 'knowledge.retrieve' "Java Tool Gateway 未返回 knowledge.retrieve，请确认 Phase50.3 已应用并重启 Java 后端"
pass "Java Tool Gateway 已注册"

echo "== 2. LangGraph health =="
LG_HEALTH=$(curl_json "${LANGGRAPH_URL}/health") || fail "LangGraph /health 不可访问"
require_contains "$LG_HEALTH" 'UP' "LangGraph /health 未返回 UP"
pass "LangGraph Runtime 存活"

echo "== 3. LangGraph ready: Runtime -> Java Tool Gateway =="
LG_READY=$(curl_json -H "X-Tenant-Id: ${TENANT_ID}" "${LANGGRAPH_URL}/ready") || fail "LangGraph /ready 不可访问"
require_contains "$LG_READY" '"status":"UP"\|"status": "UP"' "LangGraph /ready 未返回 UP，说明 Runtime 到 Java Tool Gateway 仍不通"
pass "LangGraph Runtime 能访问 Java Tool Gateway"

echo "== 4. Direct LangGraph chat =="
LG_CHAT=$(curl_json -X POST "${LANGGRAPH_URL}/api/srmp/langgraph/map-agent/chat" \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -H 'X-AI-Trace-Id: phase50-4-direct' \
  -d "$PAYLOAD") || fail "直接调用 LangGraph chat 失败"
require_contains "$LG_CHAT" 'LANGGRAPH_AGENT' "LangGraph chat 未返回 LANGGRAPH_AGENT"
require_contains "$LG_CHAT" 'toolResults' "LangGraph chat 未返回 toolResults"
pass "直接 LangGraph 编排可返回兼容响应"

echo "== 5. Java orchestrator health =="
ORCH_HEALTH=$(curl_json "${JAVA_BASE_URL}/api/agent/orchestrator/health") || fail "Java /api/agent/orchestrator/health 不可访问，请确认 Phase50.4 已应用并重启"
require_contains "$ORCH_HEALTH" 'availableProviders' "Java 编排健康接口字段缺失"
pass "Java 编排健康接口可访问"

echo "== 6. Java map-agent chat入口 =="
JAVA_CHAT=$(curl_json -X POST "${JAVA_BASE_URL}/api/agent/map-agent/chat" \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "$PAYLOAD") || fail "Java /api/agent/map-agent/chat 调用失败"
require_contains "$JAVA_CHAT" 'answer' "Java map-agent chat 未返回 answer"

if printf '%s' "$JAVA_CHAT" | grep -q 'LANGGRAPH_AGENT\|"orchestratorProvider":"langgraph"\|"orchestratorProvider": "langgraph"'; then
  pass "Java chat 已走 LangGraph 编排"
else
  if [ "$REQUIRE_LANGGRAPH" = "true" ]; then
    echo "$JAVA_CHAT" >&2
    fail "Java chat 未走 LangGraph。请确认 Java 启动环境 SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph，且已重启 srmp-admin。"
  fi
  echo "WARN: Java chat 当前未显示 LangGraph 标记，可能仍是 provider=native。需要切换时设置：SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph 后重启 Java。"
fi

pass "Phase50.4 E2E 检查完成"
