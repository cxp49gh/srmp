#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/patch-phase38-2-mapagent-llm-empty-retry.py

echo "[OK] Phase38.2 MapAgent LLM empty retry patch applied"
