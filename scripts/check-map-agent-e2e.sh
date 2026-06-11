#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ORCHESTRATOR_DIR="$ROOT_DIR/srmp-ai-orchestrator"
PYTHON_BIN="${PYTHON_BIN:-$ORCHESTRATOR_DIR/.venv/bin/python}"

if [[ ! -x "$PYTHON_BIN" ]]; then
  PYTHON_BIN="${PYTHON_BIN_FALLBACK:-python3}"
fi

cd "$ORCHESTRATOR_DIR"
PYTHONPATH=. exec "$PYTHON_BIN" "$ORCHESTRATOR_DIR/scripts/check_map_agent_e2e.py" "$@"
