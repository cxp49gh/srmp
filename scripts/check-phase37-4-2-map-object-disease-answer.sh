#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> Phase37.4.2 地图对象专病种回答验收：重度沉陷"
RESP="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/chat" \
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
        "year": 2026,
        "startStake": 78.132,
        "endStake": 78.134,
        "diseaseName": "沉陷",
        "diseaseType": "SUBSIDENCE",
        "severity": "HEAVY",
        "quantity": 5.74,
        "measureUnit": "m2"
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 5
    }
  }')"

echo "$RESP" | python3 -m json.tool | sed -n '1,260p'

python3 - "$RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""

required = ["当前对象专项处置建议", "沉陷"]
for word in required:
    if word not in answer:
        raise SystemExit("[FAIL] answer 缺少关键内容：" + word)

if not any(word in answer for word in ["路基", "基层", "排水", "不均匀变形", "结构修复"]):
    raise SystemExit("[FAIL] 沉陷回答缺少基层/路基/排水/结构修复类建议")

if "当前对象属于裂缝类病害" in answer:
    raise SystemExit("[FAIL] 沉陷对象被误判为裂缝类病害")

tools = data.get("toolResults") or payload.get("toolResults") or []
tool_names = [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]
if "knowledge.retrieve" not in tool_names:
    raise SystemExit("[FAIL] 未调用 knowledge.retrieve")

if "solution.generateDraft" in tool_names:
    raise SystemExit("[FAIL] 普通处置建议不应自动调用 solution.generateDraft")

print("[OK] Phase37.4.2 地图对象专病种回答验收通过")
PY
