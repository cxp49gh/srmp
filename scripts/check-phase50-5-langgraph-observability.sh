#!/usr/bin/env bash
set -euo pipefail

JAVA_BASE_URL="${JAVA_BASE_URL:-http://127.0.0.1:8080}"
LANGGRAPH_URL="${LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: missing command: $1" >&2
    exit 1
  }
}

curl_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: ${TENANT_ID}" \
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

require_cmd curl

printf '[1/5] LangGraph health...\n'
LG_HEALTH="$(curl_json GET "${LANGGRAPH_URL}/health")"
assert_contains "$LG_HEALTH" '"status"' 'LangGraph health should return status'

printf '[2/5] LangGraph observability summary...\n'
LG_SUMMARY="$(curl_json GET "${LANGGRAPH_URL}/api/srmp/langgraph/observability/summary")"
assert_contains "$LG_SUMMARY" '"recordCount"' 'Runtime summary should include recordCount'

printf '[3/5] Java orchestrator ops summary...\n'
JAVA_SUMMARY="$(curl_json GET "${JAVA_BASE_URL}/api/agent/orchestrator/ops/summary")"
assert_contains "$JAVA_SUMMARY" '"code"' 'Java ops summary should be wrapped by R'
assert_contains "$JAVA_SUMMARY" 'runtimeSummary' 'Java ops summary should include runtimeSummary'

printf '[4/5] Java orchestrator smoke...\n'
SMOKE_BODY='{"tenantId":"'"${TENANT_ID}"'","routeCode":"G210","year":2026,"message":"Phase50.5 smoke：请给出 G210 养护建议摘要"}'
SMOKE="$(curl_json POST "${JAVA_BASE_URL}/api/agent/orchestrator/ops/smoke" "$SMOKE_BODY")"
assert_contains "$SMOKE" '"code"' 'Smoke should be wrapped by R'
assert_contains "$SMOKE" 'response' 'Smoke should return response'

printf '[5/5] Runtime recent records...\n'
RECENT="$(curl_json GET "${LANGGRAPH_URL}/api/srmp/langgraph/observability/recent?limit=5")"
assert_contains "$RECENT" '"records"' 'Runtime recent should include records'

printf 'PASS: Phase50.5 LangGraph observability check passed.\n'
