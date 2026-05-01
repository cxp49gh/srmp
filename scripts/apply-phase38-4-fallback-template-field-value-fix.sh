#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/patch-phase38-4-solution-task-fallback-display.py
python3 scripts/patch-phase38-4-solution-tasks-page-fallback-display.py

echo "[OK] Phase38.4 方案兜底模板字段显示修复已应用"
