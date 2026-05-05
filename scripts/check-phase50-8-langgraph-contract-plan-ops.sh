#!/usr/bin/env bash
set -euo pipefail

LANGGRAPH_URL="${LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"
SKIP_LIVE="${SKIP_LIVE:-0}"
EXPECTED_STRATEGY="${EXPECTED_STRATEGY:-phase50.11-config-health-guard-v1}"

assert_file_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if [ ! -f "$file" ]; then
    echo "FAIL: missing file $file" >&2
    exit 1
  fi
  if ! grep -q "$pattern" "$file"; then
    echo "FAIL: $message" >&2
    echo "file: $file" >&2
    exit 1
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

printf '[1/5] Static files and strategy version...\n'
assert_file_contains srmp-ai-orchestrator/app/config.py "$EXPECTED_STRATEGY" "config should use current strategy version ${EXPECTED_STRATEGY}"
assert_file_contains srmp-ai-orchestrator/app/main.py 'debug/contract' 'Runtime should expose debug/contract endpoint'
assert_file_contains srmp-ai-orchestrator/app/java_tools.py 'inspect_contract' 'JavaToolGateway should inspect contract'
assert_file_contains srmp-ai-orchestrator/app/workflow.py 'request_normalize' 'workflow should keep request_normalize cumulative fix'
assert_file_contains srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAiContext.java 'Map<String, Object> geometry' 'MapAiContext should keep geometry field'

printf '[2/5] Java ops proxy endpoints...\n'
assert_file_contains srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java '@GetMapping("/contract")' 'Java ops should proxy contract diagnostics'
assert_file_contains srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java '@PostMapping("/plan")' 'Java ops should proxy plan debug'
assert_file_contains srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java 'contractDebug' 'summary should include contractDebug'

printf '[3/5] Frontend ops console hooks...\n'
assert_file_contains srmp-web-ui/src/api/orchestrator.ts 'runOrchestratorPlan' 'frontend API should expose plan debug'
assert_file_contains srmp-web-ui/src/api/orchestrator.ts 'getOrchestratorContract' 'frontend API should expose contract diagnostics'
assert_file_contains srmp-web-ui/src/views/agent/LangGraphOpsPage.vue 'Plan Debug' 'LangGraph ops page should include Plan Debug panel'
assert_file_contains srmp-web-ui/src/views/agent/LangGraphOpsPage.vue '工具契约详情' 'LangGraph ops page should include contract detail panel'

printf '[4/5] Python syntax...\n'
python3 -m py_compile srmp-ai-orchestrator/app/*.py

if [ "$SKIP_LIVE" = "1" ]; then
  printf '[5/5] Live checks skipped by SKIP_LIVE=1.\n'
  printf '\nPASS: Phase50.8 static check completed.\n'
  exit 0
fi

printf '[5/5] Runtime live endpoints...\n'
HEALTH="$(curl -sS "${LANGGRAPH_URL}/health" -H "X-Tenant-Id: ${TENANT_ID}")"
assert_contains "$HEALTH" "$EXPECTED_STRATEGY" "health should include current strategy version ${EXPECTED_STRATEGY}"
assert_contains "$HEALTH" 'request_normalize' 'health strategy should include request_normalize'

CONTRACT="$(curl -sS "${LANGGRAPH_URL}/api/srmp/langgraph/debug/contract" -H "X-Tenant-Id: ${TENANT_ID}")"
assert_contains "$CONTRACT" 'missingInJava' 'contract should include missingInJava'
assert_contains "$CONTRACT" 'runtimeAllowedTools' 'contract should include Runtime whitelist'

PLAN_BODY='{"message":"框选区域裂缝处置建议","context":{"mode":"POLYGON","geometry":{"type":"Polygon","coordinates":[]},"mapObject":{"routeCode":"G210","diseaseName":"裂缝","severity":"中度"}},"options":{"topK":2}}'
PLAN="$(curl -sS -X POST "${LANGGRAPH_URL}/api/srmp/langgraph/debug/plan" -H 'Content-Type: application/json' -H "X-Tenant-Id: ${TENANT_ID}" -d "$PLAN_BODY")"
assert_contains "$PLAN" 'REGION_ANALYSIS' 'plan should recognize polygon context as REGION_ANALYSIS'
assert_contains "$PLAN" 'gis.queryRegionSummary' 'plan should include region summary tool'
assert_contains "$PLAN" 'geometryType' 'plan should include geometry type'

printf '\nPASS: Phase50.8 LangGraph contract/plan ops check completed.\n'
