#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/srmp-ai-orchestrator"

if [ ! -d .venv ]; then
  python3 -m venv .venv
fi

# shellcheck disable=SC1091
source .venv/bin/activate
pip install -r requirements.txt

export SRMP_JAVA_BASE_URL="${SRMP_JAVA_BASE_URL:-http://127.0.0.1:8080}"
export SRMP_LANGGRAPH_ALLOW_WRITE_TOOLS="${SRMP_LANGGRAPH_ALLOW_WRITE_TOOLS:-false}"

exec uvicorn app.main:app --host 0.0.0.0 --port "${SRMP_AI_ORCHESTRATOR_PORT:-18080}" --reload
