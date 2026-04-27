#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

echo "==> 发起 AI 问答"
RESP="$(curl -s -X POST "$BASE_URL/api/agent/chat" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{"message":"分析 G210 2026 年路况，并说明 answer 是否来自大模型","context":{"routeCode":"G210","year":2026},"options":{"useBusinessData":true,"useKnowledge":true,"useOutline":false,"topK":5}}')"

TRACE_ID="$(python3 - <<PY
import json
data=json.loads('''$RESP''')
body=data.get("data") or {}
trace=body.get("trace") or {}
print(trace.get("traceId") or "")
PY
)"

if [ -z "$TRACE_ID" ]; then
  echo "[FAIL] 响应中没有 data.trace.traceId"
  echo "$RESP"
  exit 1
fi

echo "[OK] traceId=$TRACE_ID"
echo "==> 查询 trace 是否已写入"
curl -s "$BASE_URL/api/ai/traces/$TRACE_ID" -H "X-Tenant-Id: $TENANT_ID"
echo