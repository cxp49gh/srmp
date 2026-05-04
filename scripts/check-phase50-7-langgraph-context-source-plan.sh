#!/usr/bin/env bash
set -euo pipefail

LANGGRAPH_URL="${LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"

curl_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: ${TENANT_ID}" \
      -H 'X-AI-Trace-Id: phase50-7-check' \
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

printf '[1/4] Python Runtime health/strategy...\n'
HEALTH="$(curl_json GET "${LANGGRAPH_URL}/health")"
assert_contains "$HEALTH" 'phase50.7-context-source-v1' 'health should include phase50.7 strategy version'
assert_contains "$HEALTH" 'request_normalize' 'node flow should include request_normalize'

printf '[2/4] Plan debug accepts legacy context/mapObject/geometry...\n'
BODY='{"message":"框选区域裂缝处置建议","context":{"mode":"POLYGON","geometry":{"type":"Polygon","coordinates":[]},"mapObject":{"routeCode":"G210","diseaseName":"裂缝","severity":"中度"}},"options":{"topK":2}}'
PLAN="$(curl_json POST "${LANGGRAPH_URL}/api/srmp/langgraph/debug/plan" "$BODY")"
assert_contains "$PLAN" 'REGION_ANALYSIS' 'plan should recognize polygon context as REGION_ANALYSIS'
assert_contains "$PLAN" 'gis.queryRegionSummary' 'plan should include region summary tool'
assert_contains "$PLAN" 'knowledge.retrieve' 'plan should include knowledge retrieve tool'
assert_contains "$PLAN" 'geometryType' 'plan should include geometry type in context summary'

printf '[3/4] Ready includes tool gateway detail...\n'
READY="$(curl_json GET "${LANGGRAPH_URL}/ready")"
assert_contains "$READY" 'toolGateway' 'ready should include toolGateway'
assert_contains "$READY" 'strategy' 'ready should include strategy'

printf '[4/4] Java DTO keeps geometry field...\n'
test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAiContext.java
if ! grep -q 'Map<String, Object> geometry' srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAiContext.java; then
  echo 'FAIL: MapAiContext should include geometry field' >&2
  exit 1
fi

printf '\nPASS: Phase50.7 LangGraph context/source/plan check completed.\n'
