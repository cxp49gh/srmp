#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> Phase37.4.4 回答展示收口与评定单元病害联动验收"
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
        "rqi": 78.012,
        "rdi": 93.233,
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

echo "$RESP" | python3 -m json.tool | sed -n '1,300p'

python3 - "$RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""

if "当前评定单元专项分析" not in answer:
    raise SystemExit("[FAIL] answer 缺少当前评定单元专项分析")
if "建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核" in answer:
    raise SystemExit("[FAIL] 泛化建议未被移除")
if "####" in answer:
    raise SystemExit("[FAIL] answer 仍包含 Markdown 四级标题 ####")
if "参考依据" in answer:
    raise SystemExit("[FAIL] 正文仍包含参考依据，可能与依据面板重复")
for word in ["MQI", "PQI", "PCI"]:
    if word not in answer:
        raise SystemExit("[FAIL] answer 缺少指标：" + word)

tools = data.get("toolResults") or payload.get("toolResults") or []
tool_names = [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]
if "knowledge.retrieve" not in tool_names:
    raise SystemExit("[FAIL] 未调用 knowledge.retrieve")
if "gis.queryDiseasesByStakeRange" not in tool_names:
    raise SystemExit("[FAIL] 未调用 gis.queryDiseasesByStakeRange")

print("[OK] Phase37.4.4 回答展示收口与评定单元病害联动验收通过")
PY
