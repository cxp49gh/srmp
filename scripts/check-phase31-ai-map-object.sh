#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

REQ='{
  "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
  "context":{"routeCode":"G210","year":2026},
  "mapObject":{
    "objectType":"DISEASE",
    "routeCode":"G210",
    "startStake":69.007,
    "endStake":69.034,
    "diseaseName":"修补损坏",
    "severity":"MEDIUM",
    "quantity":11.02,
    "measureUnit":"m2",
    "year":2026
  },
  "options":{"useBusinessData":true,"useKnowledge":false,"useOutline":false,"topK":5}
}'

echo "==> POST /api/agent/chat with mapObject=DISEASE"
RESP="$(curl -fsS -X POST "$BASE_URL/api/agent/chat" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d "$REQ")"

echo "$RESP" | python3 -m json.tool | head -220

python3 - "$RESP" <<'PY'
import json, sys, re
payload=json.loads(sys.argv[1])
data=payload.get("data") or {}
answer=payload.get("answer") or data.get("answer") or ""
warnings=[]
if not (data.get("mapObjectUsed") or payload.get("mapObjectUsed")):
    warnings.append("mapObjectUsed 不是 true")
if "<think>" in answer.lower() or "<thinking>" in answer.lower():
    warnings.append("answer 仍包含 think/thinking 标签")
if "病害总数" in answer or "TOP10" in answer or "TOP 10" in answer:
    warnings.append("单病害回答疑似仍输出路线级病害统计")
if "当前地图对象" not in answer and "当前地图选中对象" not in answer:
    warnings.append("answer 未体现当前地图对象")
if warnings:
    print("[WARN] AI 地图对象检查发现问题：")
    for w in warnings:
        print(" -", w)
    sys.exit(1)
print("[OK] AI 地图对象问答检查通过")
PY
