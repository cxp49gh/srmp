#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> Phase37.5.1 disease-pothole 回归修复验证"
RESP="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/run" \
  -H "Content-Type: application/json" \
  -d '{
    "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
    "mapContext":{
      "mode":"OBJECT",
      "routeCode":"G210",
      "year":2026,
      "mapObject":{
        "objectType":"DISEASE",
        "routeCode":"G210",
        "year":2026,
        "startStake":57.523,
        "endStake":57.527,
        "diseaseName":"坑槽",
        "diseaseType":"POTHOLE",
        "severity":"MEDIUM",
        "quantity":1.32,
        "measureUnit":"m2"
      }
    },
    "options":{
      "useKnowledge":true,
      "useBusinessData":true,
      "useTools":true,
      "topK":5
    }
  }')"

echo "$RESP" | python3 -m json.tool | sed -n '1,260p'

python3 - "$RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""
tools=data.get("toolResults") or payload.get("toolResults") or []
sources=data.get("sources") or payload.get("sources") or []

if "当前对象专项处置建议" not in answer:
    raise SystemExit("[FAIL] disease-pothole: answer 缺少专项标题：当前对象专项处置建议")
if "坑槽" not in answer:
    raise SystemExit("[FAIL] disease-pothole: answer 缺少坑槽")
if not any(w in answer for w in ["切割", "清理", "压实", "热补", "冷补", "基层"]):
    raise SystemExit("[FAIL] disease-pothole: answer 缺少坑槽处置术语")
if "knowledge.retrieve" not in [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]:
    raise SystemExit("[FAIL] disease-pothole: 未调用 knowledge.retrieve")
if not sources:
    raise SystemExit("[FAIL] disease-pothole: sources 为空")
if "solution.generateDraft" in [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]:
    raise SystemExit("[FAIL] disease-pothole: 普通分析不应自动调用 solution.generateDraft")
print("[OK] disease-pothole 回归修复通过")
PY
