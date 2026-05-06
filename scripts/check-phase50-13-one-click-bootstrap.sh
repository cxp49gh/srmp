#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

contains() {
  local file="$1"
  local needle="$2"
  grep -F -- "$needle" "$file" >/dev/null || fail "$file missing: $needle"
}

test -f docker-compose.yml || fail "docker-compose.yml missing"
test -f docker-compose.app.yml || fail "docker-compose.app.yml missing"
test -f docker-compose.langgraph.yml || fail "docker-compose.langgraph.yml missing"
test -f scripts/srmp-one-click-start.sh || fail "srmp-one-click-start.sh missing"
test -f scripts/srmp-check-ready.sh || fail "srmp-check-ready.sh missing"
test -f docs/phase50_13_one_click_bootstrap.md || fail "phase doc missing"

bash -n scripts/srmp-one-click-start.sh
bash -n scripts/srmp-check-ready.sh

contains docker-compose.app.yml "SRMP_AI_ORCHESTRATOR_PROVIDER"
contains docker-compose.app.yml "SRMP_LANGGRAPH_URL"
contains docker-compose.app.yml "http://srmp-ai-orchestrator:18080"
contains docker-compose.langgraph.yml "SRMP_JAVA_BASE_URL"
contains docker-compose.langgraph.yml "http://backend:8080"
contains docker-compose.langgraph.yml "SRMP_LANGGRAPH_AUDIT_PERSIST_ENABLED"
contains docker-compose.langgraph.yml "srmp_langgraph_audit"

contains scripts/srmp-one-click-start.sh "docker-compose.langgraph.yml"
contains scripts/srmp-one-click-start.sh "--no-orchestrator"
contains scripts/srmp-one-click-start.sh "srmp-ai-orchestrator"
contains scripts/srmp-one-click-start.sh "SRMP_AI_ORCHESTRATOR_PROVIDER"
contains scripts/srmp-one-click-start.sh "SRMP_LANGGRAPH_URL"

contains scripts/srmp-check-ready.sh "ORCHESTRATOR_URL"
contains scripts/srmp-check-ready.sh "--no-orchestrator"
contains scripts/srmp-check-ready.sh "ai_solution_template"
contains scripts/srmp-check-ready.sh "ai_solution_template_version"
contains scripts/srmp-check-ready.sh "ai_trace_log"
contains scripts/srmp-check-ready.sh "/api/agent/orchestrator/ops/summary"
contains scripts/srmp-check-ready.sh "/api/agent/orchestrator/ops/config"
contains scripts/srmp-check-ready.sh "/api/agent/orchestrator/ops/health-detail"
contains scripts/srmp-check-ready.sh "/ready"

contains docs/one-click-start-guide.md "srmp-ai-orchestrator"
contains docs/one-click-start-guide.md "/agent/langgraph-ops"
contains docs/phase50_13_one_click_bootstrap.md "LangGraph"

if command -v docker >/dev/null 2>&1; then
  docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml config >/tmp/srmp-phase50-13-compose.yml
  grep -F "srmp-ai-orchestrator" /tmp/srmp-phase50-13-compose.yml >/dev/null || fail "combined compose missing srmp-ai-orchestrator"
  grep -F "srmp-backend" /tmp/srmp-phase50-13-compose.yml >/dev/null || fail "combined compose missing srmp-backend"
  grep -F "srmp-frontend" /tmp/srmp-phase50-13-compose.yml >/dev/null || fail "combined compose missing srmp-frontend"
fi

echo "[PASS] Phase50.13 one-click LangGraph bootstrap static checks passed"
