#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
SOURCE_TYPE="${SOURCE_TYPE:-MANUAL}"
FORCE="${FORCE:-true}"
LIMIT="${LIMIT:-}"

payload="{\"tenantId\":\"$TENANT_ID\",\"sourceType\":\"$SOURCE_TYPE\",\"force\":$FORCE"
if [ -n "$LIMIT" ]; then
  payload="$payload,\"limit\":$LIMIT"
fi
payload="$payload}"

echo "==> POST /api/ai/knowledge/reindex"
curl -fsS -X POST "$BASE_URL/api/ai/knowledge/reindex" \
  -H "Content-Type: application/json" \
  -d "$payload" | python3 -m json.tool

echo "==> GET /api/ai/knowledge/stats"
curl -fsS "$BASE_URL/api/ai/knowledge/stats?tenantId=$TENANT_ID" | python3 -m json.tool
