#!/usr/bin/env bash
set -euo pipefail

JAVA_BASE_URL="${JAVA_BASE_URL:-http://127.0.0.1:8080}"
LANGGRAPH_URL="${LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"
REQUIRE_LANGGRAPH="${REQUIRE_LANGGRAPH:-false}"
EXPECTED_STRATEGY="${EXPECTED_STRATEGY:-phase50.11-config-health-guard-v1}"
SKIP_LIVE="${SKIP_LIVE:-0}"

curl_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: ${TENANT_ID}" \
      -H 'X-AI-Trace-Id: phase50-6-check' \
      -d "$body"
  else
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: ${TENANT_ID}"
  fi
}

assert_contains() {
  local text="$1"
  local pattern="$2"
  local message="$3"
  if ! echo "$text" | grep -q "$pattern"; then
    echo "FAIL: $message" >&2
    echo "$text" >&2
    exit 1
  fi
}

if [ "$SKIP_LIVE" = "1" ]; then
  printf '[1/3] Static strategy defaults...\n'
  grep -q "$EXPECTED_STRATEGY" srmp-ai-orchestrator/app/config.py
  printf '[2/3] Static workflow nodes...\n'
  grep -q 'quality_guard' srmp-ai-orchestrator/app/workflow.py
  grep -q 'tool_planning' srmp-ai-orchestrator/app/workflow.py
  printf '[3/3] Python syntax...\n'
  python3 -m py_compile srmp-ai-orchestrator/app/*.py
  printf '\nPASS: Phase50.6 LangGraph read-only strategy static check completed.\n'
  exit 0
fi

printf '[1/5] LangGraph strategy metadata...\n'
STRATEGY="$(curl_json GET "${LANGGRAPH_URL}/api/srmp/langgraph/strategy")"
assert_contains "$STRATEGY" "$EXPECTED_STRATEGY" "strategy should include ${EXPECTED_STRATEGY}"
assert_contains "$STRATEGY" 'nodeFlow' 'strategy should include nodeFlow'

printf '[2/5] LangGraph ready...\n'
READY="$(curl_json GET "${LANGGRAPH_URL}/ready")"
assert_contains "$READY" 'toolGateway' 'ready should include toolGateway status'

printf '[3/5] LangGraph direct chat strategy smoke...\n'
BODY='{"message":"Phase50.6 smoke：请分析 G210 当前对象养护建议","mapContext":{"tenantId":"'"${TENANT_ID}"'","mode":"ROUTE","routeCode":"G210","year":2026,"mapObject":{"objectType":"disease","diseaseName":"裂缝","severity":"中度","routeCode":"G210"}},"options":{"topK":3}}'
LG_CHAT="$(curl_json POST "${LANGGRAPH_URL}/api/srmp/langgraph/map-agent/run" "$BODY")"
assert_contains "$LG_CHAT" 'strategyVersion' 'LangGraph response should include strategyVersion'
assert_contains "$LG_CHAT" 'toolPlan' 'LangGraph response should include toolPlan'
assert_contains "$LG_CHAT" 'evidence' 'LangGraph response should include evidence'
assert_contains "$LG_CHAT" 'quality_guard' 'LangGraph trace should include quality_guard'

printf '[4/5] Java orchestrator path smoke...\n'
JAVA_CHAT="$(curl_json POST "${JAVA_BASE_URL}/api/agent/map-agent/run" "$BODY")"
assert_contains "$JAVA_CHAT" 'code' 'Java chat should be wrapped by ApiResult/R'
if [ "$REQUIRE_LANGGRAPH" = "true" ]; then
  assert_contains "$JAVA_CHAT" 'langgraph' 'Java chat should route to langgraph when REQUIRE_LANGGRAPH=true'
fi

printf '[5/5] Runtime observability recent...\n'
RECENT="$(curl_json GET "${LANGGRAPH_URL}/api/srmp/langgraph/observability/recent?limit=5")"
assert_contains "$RECENT" 'records' 'recent response should be available'

printf '\nPASS: Phase50.6 LangGraph read-only strategy check completed.\n'
