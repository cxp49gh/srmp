#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

RESP="$(curl -s -X POST "$BASE_URL/api/agent/chat" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{
    "message":"分析当前地图选中对象，说明主要问题并给出养护建议",
    "context":{"routeCode":"G210","year":2026},
    "mapObject":{
      "objectType":"ROAD_ROUTE",
      "routeCode":"G210",
      "year":2026
    },
    "options":{"useBusinessData":true,"useKnowledge":false,"useOutline":false,"topK":5}
  }')"

python3 - "$RESP" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
print(json.dumps(payload, ensure_ascii=False, indent=2)[:3000])
data = payload.get("data") or {}
if payload.get("code") not in (0, None):
    print("[FAIL] 接口返回 code != 0")
    sys.exit(1)
if not (data.get("mapObjectUsed") or payload.get("mapObjectUsed")):
    print("[FAIL] mapObjectUsed 未返回 true")
    sys.exit(1)
print("[OK] map object AI context response detected")
PY
