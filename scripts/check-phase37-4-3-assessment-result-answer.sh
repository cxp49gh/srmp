#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> Phase37.4.3 评定结果对象专项回答验收"
RESP="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "ASSESSMENT_RESULT",
        "routeCode": "G210",
        "year": 2026,
        "startStake": 76.534,
        "endStake": 77.334,
        "mqi": 74.729,
        "pqi": 70.059,
        "pci": 70.525,
        "grade": "MEDIUM"
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 5
    }
  }')"

echo "$RESP" | python3 -m json.tool | sed -n '1,280p'

python3 - "$RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""

required = ["当前评定单元专项分析", "MQI", "PQI", "PCI"]
for word in required:
    if word not in answer:
        raise SystemExit("[FAIL] answer 缺少关键内容：" + word)

if not any(word in answer for word in ["路面使用性能", "路面损坏状况", "主要短板", "破损类病害"]):
    raise SystemExit("[FAIL] 评定结果回答缺少指标短板解释")

if not any(word in answer for word in ["局部修补", "封层", "薄层罩面", "中修", "近期养护计划"]):
    raise SystemExit("[FAIL] 评定结果回答缺少养护处置建议")

if "当前对象专项处置建议" in answer and "病害为" in answer:
    raise SystemExit("[FAIL] ASSESSMENT_RESULT 被误判为 DISEASE 病害对象")

tools = data.get("toolResults") or payload.get("toolResults") or []
tool_names = [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]
if "knowledge.retrieve" not in tool_names:
    raise SystemExit("[FAIL] 未调用 knowledge.retrieve")

print("[OK] Phase37.4.3 评定结果对象专项回答验收通过")
PY
