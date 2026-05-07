#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message"
    echo "       file: $file"
    echo "       pattern: $pattern"
    exit 1
  fi
  echo "[OK] $message"
}

reject_pattern() {
  local path="$1"
  local pattern="$2"
  local message="$3"
  if rg -n "$pattern" "$path" >/tmp/srmp-phase50-18-rg.txt; then
    echo "[FAIL] $message"
    cat /tmp/srmp-phase50-18-rg.txt
    exit 1
  fi
  echo "[OK] $message"
}

require_pattern "srmp-ai-orchestrator/app/schemas.py" "class MapAgentRunRequest" "Python run request schema exists"
require_pattern "srmp-ai-orchestrator/app/schemas.py" "class MapAgentRunResponse" "Python run response schema exists"
require_pattern "srmp-ai-orchestrator/app/main.py" "/api/srmp/langgraph/map-agent/run" "Python run endpoint exists"
require_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/controller/MapAiAgentController.java" "/run" "Java run endpoint exists"
require_pattern "srmp-admin/src/main/resources/application-dev.yml" "/api/srmp/langgraph/map-agent/run" "Java dev config points to Python run endpoint"
require_pattern "docker-compose.app.yml" "/api/srmp/langgraph/map-agent/run" "Docker app config points to Python run endpoint"
require_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/AgentOrchestratorRouter.java" "orchestratorFallback.*false" "Java router disables native fallback"
require_pattern "srmp-web-ui/src/api/agent.ts" "mapAgentRun" "Frontend mapAgentRun exists"
require_pattern "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue" "mapAgentRun" "One-map chat uses mapAgentRun"
require_pattern "srmp-web-ui/src/views/gis/OneMap.vue" "GENERATE_REGION_SOLUTION" "Region solution uses run action"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "actionBuckets" "Ops page exposes action buckets"

test ! -f "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/NativeMapAgentOrchestrator.java" || { echo "[FAIL] native orchestrator file still exists"; exit 1; }
test ! -f "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlannerImpl.java" || { echo "[FAIL] native planner file still exists"; exit 1; }

reject_pattern "srmp-web-ui/src" "mapAgentChat|generateMapObjectSolution|generateMapRegionSolution|/api/agent/map-agent/chat|/api/agent/map-object/solution|/api/gis/map-region/solution" "old frontend AI helpers and endpoints removed"
reject_pattern "srmp-web-ui/src" "fallbackToNative" "frontend no longer displays native fallback"
reject_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator" "fallbackToNative|NativeMapAgentOrchestrator|provider\\(\\).*native" "old Java native provider removed"
reject_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent" "/chat|map-agent/chat" "old Java map-agent chat endpoint removed"
reject_pattern "srmp-ai-orchestrator/app" "map-agent/chat" "old Python map-agent chat endpoint removed"
reject_pattern "srmp-admin/src/main/resources" "map-agent/chat|fallback-to-native|SRMP_AI_ORCHESTRATOR_PROVIDER|SRMP_LANGGRAPH_FALLBACK_TO_NATIVE" "runtime YAML no longer points to native or old chat"
reject_pattern "docker-compose.app.yml" "map-agent/chat|SRMP_AI_ORCHESTRATOR_PROVIDER|SRMP_LANGGRAPH_FALLBACK_TO_NATIVE" "Docker runtime env no longer points to native or old chat"

srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
mvn -pl srmp-agent,srmp-gis test
npm --prefix srmp-web-ui run build

echo "[OK] Phase50.18 LangGraph-first map agent checks passed"
