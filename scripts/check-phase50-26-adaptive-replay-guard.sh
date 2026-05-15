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

PYTHON_BIN="${PYTHON_BIN:-$ROOT_DIR/srmp-ai-orchestrator/.venv/bin/python}"
if [ ! -x "$PYTHON_BIN" ] && [ -x "$HOME/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3" ]; then
  PYTHON_BIN="$HOME/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3"
fi
if [ ! -x "$PYTHON_BIN" ]; then
  PYTHON_BIN="$(command -v python3 || true)"
fi
if [ -z "$PYTHON_BIN" ]; then
  echo "[FAIL] python not found; set PYTHON_BIN or create srmp-ai-orchestrator/.venv" >&2
  exit 1
fi

require_file() {
  local file="$1"
  test -f "$file" || {
    echo "[FAIL] missing file: $file" >&2
    exit 1
  }
  echo "[OK] file exists: $file"
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    echo "       file: $file" >&2
    echo "       pattern: $pattern" >&2
    exit 1
  fi
  echo "[OK] $message"
}

require_file "docs/superpowers/specs/2026-05-13-phase50-26-adaptive-replay-guard-design.md"
require_file "docs/phase50_26_adaptive_replay_guard.md"
require_file "srmp-ai-orchestrator/tests/test_replay_adaptive_compare.py"

require_pattern "srmp-ai-orchestrator/app/config.py" "SRMP_MAX_ADAPTIVE_ADDED_TOOLS" "Runtime config exposes adaptive added-tool budget env"
require_pattern "srmp-ai-orchestrator/app/main.py" "adaptiveMode" "Runtime replay accepts adaptiveMode query"
require_pattern "srmp-ai-orchestrator/app/main.py" "adaptiveMode=compare requires execute=true" "Runtime rejects compare without execute replay"
require_pattern "srmp-ai-orchestrator/app/main.py" "_build_adaptive_compare" "Runtime builds baseline/adaptive compare payload"
require_pattern "srmp-ai-orchestrator/app/adaptive_planner.py" "maxAdaptiveAddedTools" "Adaptive planner reads request-level added-tool budget"
require_pattern "srmp-ai-orchestrator/app/adaptive_planner.py" "evidenceBefore" "Adaptive summary includes evidenceBefore"
require_pattern "srmp-ai-orchestrator/app/adaptive_planner.py" "evidenceAfter" "Adaptive summary includes evidenceAfter"
require_pattern "srmp-ai-orchestrator/app/workflow.py" "maxAdaptiveAddedTools" "Strategy metadata exposes adaptive added-tool budget"
require_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java" "adaptiveMode" "Java ops replay proxies adaptiveMode"
require_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAgentRunResponse.java" "planExecution" "Java map-agent response accepts Runtime planExecution"
require_pattern "srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java" "replayRuntimePathEncodesAdaptiveMode" "Java replay adaptiveMode path test exists"
require_pattern "srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/impl/RemoteLangGraphOrchestratorContractTest.java" "mapAgentRunResponseAcceptsRuntimePlanExecution" "Java Runtime map-agent response contract test exists"
require_pattern "srmp-web-ui/src/api/orchestrator.ts" "params: \\{ execute, adaptiveMode \\}" "Frontend replay API sends adaptiveMode"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "自适应对比" "Ops page exposes adaptive compare action"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "evidenceImproved" "Ops page displays evidence improvement"
require_pattern "srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs" "adaptive replay compare" "Frontend adaptive replay compare test exists"

PYTHONPATH="$ROOT_DIR/srmp-ai-orchestrator" "$PYTHON_BIN" -m unittest \
  tests.test_adaptive_planner \
  tests.test_adaptive_workflow \
  tests.test_runtime_config_adaptive \
  tests.test_replay_adaptive_compare
echo "[OK] Python adaptive replay guard tests passed"

node --no-warnings --test srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs
echo "[OK] Frontend adaptive ops tests passed"

mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest,RemoteLangGraphOrchestratorContractTest test
echo "[OK] Java ops replay proxy test passed"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  npm --prefix srmp-web-ui run build
  echo "[OK] Frontend production build passed"
fi

echo "[OK] Phase50.26 adaptive replay guard checks passed"
