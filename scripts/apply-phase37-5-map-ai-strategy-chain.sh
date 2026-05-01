#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
python3 scripts/patch-phase37-5-map-ai-strategy-chain.py
