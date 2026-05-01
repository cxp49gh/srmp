#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> Phase37.4.6 路段对象专项回答验收"
SECTION_RESP="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "ROAD_SECTION",
        "routeCode": "G210",
        "sectionName": "G210 示例路段",
        "startStake": 70.000,
        "endStake": 80.000,
        "lengthKm": 10.0,
        "pavementType": "沥青混凝土",
        "laneCount": 2
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 5
    }
  }')"

echo "$SECTION_RESP" | python3 -m json.tool | sed -n '1,260p'

python3 - "$SECTION_RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""
if "当前路段专项分析" not in answer:
    raise SystemExit("[FAIL] 路段回答缺少“当前路段专项分析”")
for word in ["主要问题", "成因判断", "养护处置建议"]:
    if word not in answer:
        raise SystemExit("[FAIL] 路段回答缺少：" + word)
tools = data.get("toolResults") or payload.get("toolResults") or []
names = [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]
for tool in ["knowledge.retrieve", "gis.queryAssessmentResults", "gis.queryDiseases"]:
    if tool not in names:
        raise SystemExit("[FAIL] 路段对象未调用工具：" + tool)
print("[OK] 路段对象专项回答验收通过")
PY

echo "==> Phase37.4.6 路线对象专项回答验收"
ROUTE_RESP="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "ROAD_ROUTE",
        "routeCode": "G210",
        "routeName": "G210 测试路线",
        "routeType": "NATIONAL",
        "adminGrade": "国道",
        "technicalGrade": "二级公路",
        "startStake": 0.000,
        "endStake": 100.000,
        "lengthKm": 100.0
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 5
    }
  }')"

echo "$ROUTE_RESP" | python3 -m json.tool | sed -n '1,260p'

python3 - "$ROUTE_RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""
if "当前路线专项分析" not in answer:
    raise SystemExit("[FAIL] 路线回答缺少“当前路线专项分析”")
for word in ["路线级", "低分单元", "病害热点", "养护"]:
    if word not in answer:
        raise SystemExit("[FAIL] 路线回答缺少：" + word)
tools = data.get("toolResults") or payload.get("toolResults") or []
names = [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]
for tool in ["knowledge.retrieve", "gis.queryAssessmentResults", "gis.queryDiseases"]:
    if tool not in names:
        raise SystemExit("[FAIL] 路线对象未调用工具：" + tool)
print("[OK] 路线对象专项回答验收通过")
PY

echo "[OK] Phase37.4.6 路线/路段对象专项回答验收通过"
