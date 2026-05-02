#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/patch-phase38-5-map-agent-cleanup.py

echo "[OK] Phase38.5 代码收口 patch 已应用"
