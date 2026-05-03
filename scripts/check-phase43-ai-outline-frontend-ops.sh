#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "[1/4] LLM health"
curl -fsS "$BASE_URL/api/ai/llm/health?probe=false" | python3 -m json.tool | sed -n '1,160p'

echo "[2/4] Outline knowledge stats"
curl -fsS "$BASE_URL/api/outline/knowledge-stats" | python3 -m json.tool | sed -n '1,160p'

echo "[3/4] Auto sync configs"
curl -fsS "$BASE_URL/api/outline/auto-sync/configs" | python3 -m json.tool | sed -n '1,200p'

echo "[4/4] Auto sync runs"
curl -fsS "$BASE_URL/api/outline/auto-sync/runs?limit=20" | python3 -m json.tool | sed -n '1,220p'

echo "[OK] Phase43 AI/Outline frontend ops check completed"
