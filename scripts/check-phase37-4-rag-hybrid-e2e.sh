#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

echo "==> 知识库 Hybrid 检索验证"
SEARCH="$(curl -fsS -X POST "$BASE_URL/api/ai/knowledge/search" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"query\":\"这个对象怎么处理 G210 中度 修补损坏\",\"topK\":8,\"sourceTypes\":[\"MANUAL\"]}")"

echo "$SEARCH" | python3 -m json.tool | sed -n '1,220p'

python3 - "$SEARCH" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
hits=data.get("hits") or []
if not hits:
    raise SystemExit("[FAIL] 知识检索无命中")
if data.get("searchMode") not in ("HYBRID", "VECTOR"):
    raise SystemExit("[FAIL] searchMode 不是 HYBRID/VECTOR：" + str(data.get("searchMode")))
if data.get("vectorUsed") is not True:
    raise SystemExit("[FAIL] vectorUsed != true")
print("[OK] knowledge search:", data.get("searchMode"), data.get("retrievalStrategy"), "hits=", len(hits))
PY

echo "==> 一张图 Agent Hybrid RAG 验证"
AGENT="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/run" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "分析当前对象，给出处置建议",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "DISEASE",
        "routeCode": "G210",
        "diseaseName": "修补损坏",
        "severity": "MEDIUM"
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 8
    }
  }')"

echo "$AGENT" | python3 -m json.tool | sed -n '1,260p'

python3 - "$AGENT" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
tools=data.get("toolResults") or payload.get("toolResults") or []
kr=None
for t in tools:
    if isinstance(t, dict) and (t.get("toolName") == "knowledge.retrieve" or t.get("name") == "knowledge.retrieve"):
        kr=t
        break
if not kr:
    raise SystemExit("[FAIL] 未调用 knowledge.retrieve")
tool_data=kr.get("data") or {}
if not tool_data.get("rewrittenQuery"):
    raise SystemExit("[FAIL] knowledge.retrieve 未返回 rewrittenQuery")
if not tool_data.get("retrievalStrategy"):
    raise SystemExit("[FAIL] knowledge.retrieve 未返回 retrievalStrategy")
if tool_data.get("vectorUsed") is not True:
    raise SystemExit("[FAIL] Agent knowledge.retrieve vectorUsed != true")
print("[OK] map agent evidence:", tool_data.get("retrievalStrategy"), tool_data.get("searchMode"))
PY

echo "[OK] Phase37.4 RAG Hybrid + 依据面板后端验收通过"
