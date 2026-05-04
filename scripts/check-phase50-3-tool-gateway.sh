#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${SRMP_BASE_URL:-http://127.0.0.1:8080}"
TENANT_ID="${SRMP_TENANT_ID:-default}"

if [ "${1:-}" = "--structure-only" ]; then
  test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/controller/AgentToolGatewayController.java
  test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/dto/AgentToolExecuteRequest.java
  test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/dto/AgentToolInfo.java
  grep -q 'RequestMapping("/api/agent/tools")' srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/controller/AgentToolGatewayController.java
  grep -q 'PostMapping("/execute")' srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/controller/AgentToolGatewayController.java
  echo "PASS: Phase50.3 Tool Gateway source structure exists."
  exit 0
fi

echo "[1/3] Check Java Tool Gateway list endpoint: ${BASE_URL}/api/agent/tools"
TOOLS_JSON="$(curl -fsS -H "X-Tenant-Id: ${TENANT_ID}" "${BASE_URL}/api/agent/tools")"
echo "${TOOLS_JSON}"
echo "${TOOLS_JSON}" | grep -q 'knowledge.retrieve'

echo "[2/3] Check Java Tool Gateway execute endpoint"
EXEC_JSON="$(curl -fsS -X POST "${BASE_URL}/api/agent/tools/execute" \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "toolName":"gis.queryRegionSummary",
    "tenantId":"'"${TENANT_ID}"'",
    "traceId":"phase50-3-smoke",
    "userQuestion":"统计 G210 路况",
    "mapContext":{"tenantId":"'"${TENANT_ID}"'","mode":"ROUTE","routeCode":"G210","year":2026},
    "args":{}
  }')"
echo "${EXEC_JSON}"
echo "${EXEC_JSON}" | grep -q 'gis.queryRegionSummary'

echo "[3/3] Check LangGraph proxy tools endpoint if available"
if curl -fsS "${SRMP_LANGGRAPH_URL:-http://127.0.0.1:18080}/health" >/dev/null 2>&1; then
  curl -fsS -H "X-Tenant-Id: ${TENANT_ID}" "${SRMP_LANGGRAPH_URL:-http://127.0.0.1:18080}/api/srmp/langgraph/tools" | grep -q 'knowledge.retrieve'
  echo "PASS: LangGraph can proxy Java Tool Gateway."
else
  echo "SKIP: LangGraph runtime is not reachable; Java Gateway checks passed."
fi

echo "PASS: Phase50.3 Tool Gateway smoke test passed."
