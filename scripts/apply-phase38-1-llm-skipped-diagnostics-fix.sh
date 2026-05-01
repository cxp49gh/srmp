#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/patch-phase38-1-map-agent-llm-skipped-fix.py

echo "[OK] Phase38.1 LLM SKIPPED diagnostics patch applied"
