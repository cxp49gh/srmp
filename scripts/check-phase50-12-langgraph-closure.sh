#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

pass() {
  echo "[PASS] $*"
}

WORKFLOW_FILE="srmp-ai-orchestrator/app/workflow.py"
CONFIG_FILE="srmp-ai-orchestrator/app/config.py"
OPS_API_FILE="srmp-web-ui/src/api/orchestrator.ts"
OPS_PAGE_FILE="srmp-web-ui/src/views/agent/LangGraphOpsPage.vue"

[ -f "$WORKFLOW_FILE" ] || fail "missing $WORKFLOW_FILE"
[ -f "$CONFIG_FILE" ] || fail "missing $CONFIG_FILE"
[ -f "$OPS_API_FILE" ] || fail "missing $OPS_API_FILE"
[ -f "$OPS_PAGE_FILE" ] || fail "missing $OPS_PAGE_FILE"

grep -q 'phase50.11-config-health-guard-v1' "$CONFIG_FILE" \
  || fail "runtime strategy should keep Phase50.11 config-health-guard default"

if grep -q 'builder.add_edge("tool_planning", END)' "$WORKFLOW_FILE"; then
  fail "LangGraph runtime must not terminate at tool_planning; it should execute tools, fuse evidence, generate answer, and run quality_guard"
fi

grep -q 'builder.add_edge("quality_guard", END)' "$WORKFLOW_FILE" \
  || fail "LangGraph runtime should terminate at quality_guard"
grep -q 'def _node_handler' "$WORKFLOW_FILE" \
  || fail "sequential plan execution should map node names to handlers"
grep -q 'node == "tool_planning"' "$WORKFLOW_FILE" \
  || fail "sequential plan execution should resolve tool_planning to _tool_plan"
grep -Fq 'tool_plan: List[ToolCall]' "$WORKFLOW_FILE" \
  || fail "AgentState must declare tool_plan so compiled LangGraph preserves planned tools for tool_execute"

grep -q 'getOrchestratorConfig' "$OPS_API_FILE" \
  || fail "frontend API should expose runtime config"
grep -q 'getOrchestratorHealthDetail' "$OPS_API_FILE" \
  || fail "frontend API should expose health-detail"
grep -q 'pruneOrchestratorAudit' "$OPS_API_FILE" \
  || fail "frontend API should expose audit pruning"

grep -q '配置健康' "$OPS_PAGE_FILE" \
  || fail "LangGraph ops page should show configuration health"
grep -q '审计持久化' "$OPS_PAGE_FILE" \
  || fail "LangGraph ops page should show audit persistence"

python3 -m py_compile srmp-ai-orchestrator/app/*.py

pass "Phase50.12 LangGraph closure static checks passed"
