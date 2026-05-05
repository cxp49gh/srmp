#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RUNTIME_URL="${SRMP_LANGGRAPH_URL:-http://127.0.0.1:18080}"
JAVA_URL="${SRMP_JAVA_BASE_URL:-http://127.0.0.1:8080}"
SKIP_LIVE="${SKIP_LIVE:-0}"
EXPECTED_STRATEGY="${EXPECTED_STRATEGY:-phase50.11-config-health-guard-v1}"
EXPECTED_APP_VERSION="${EXPECTED_APP_VERSION:-50.11.0}"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

pass() {
  echo "[PASS] $*"
}

require_file() {
  [ -f "$1" ] || fail "missing file: $1"
}

contains() {
  local file="$1"
  local pattern="$2"
  grep -q "$pattern" "$file" || fail "pattern not found in $file: $pattern"
}

require_file "srmp-ai-orchestrator/app/main.py"
require_file "srmp-ai-orchestrator/app/observability.py"
require_file "srmp-ai-orchestrator/app/config.py"
require_file "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java"
require_file "srmp-web-ui/src/api/orchestrator.ts"
require_file "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue"
require_file "docs/phase50_9_langgraph_runtime_replay_export.md"

contains "srmp-ai-orchestrator/app/main.py" "observability/record"
contains "srmp-ai-orchestrator/app/main.py" "observability/export"
contains "srmp-ai-orchestrator/app/main.py" "debug/replay"
contains "srmp-ai-orchestrator/app/observability.py" "requestPayload"
contains "srmp-ai-orchestrator/app/observability.py" "export_bundle"
contains "srmp-ai-orchestrator/app/config.py" "$EXPECTED_STRATEGY"
contains "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java" "/replay/{recordId}"
contains "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java" "/export"
contains "srmp-web-ui/src/api/orchestrator.ts" "replayOrchestratorRecord"
contains "srmp-web-ui/src/api/orchestrator.ts" "exportOrchestratorDiagnostics"
contains "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "Plan回放"
contains "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "导出诊断"

python3 -m py_compile srmp-ai-orchestrator/app/*.py
pass "Python files compile"

if [ "$SKIP_LIVE" = "1" ]; then
  pass "static checks passed; live checks skipped"
  exit 0
fi

curl -fsS "$RUNTIME_URL/health" | grep -q "$EXPECTED_APP_VERSION" || fail "Runtime /health does not expose $EXPECTED_APP_VERSION"
pass "Runtime health ok"

curl -fsS "$RUNTIME_URL/api/srmp/langgraph/observability/export?limit=5" | grep -q "$EXPECTED_STRATEGY" || fail "Runtime export missing strategy version $EXPECTED_STRATEGY"
pass "Runtime export ok"

curl -fsS "$JAVA_URL/api/agent/orchestrator/ops/export?limit=5" >/tmp/srmp_phase50_9_export.json || fail "Java ops export failed"
grep -q "observability/export" /tmp/srmp_phase50_9_export.json || fail "Java export did not proxy Runtime export"
pass "Java ops export proxy ok"

pass "Phase50.9 runtime replay/export checks passed"
