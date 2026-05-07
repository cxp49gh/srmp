#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> LLM health"
curl -fsS "$BASE_URL/api/ai/llm/health?probe=true" | python3 -m json.tool

echo "==> Map Agent LLM step check"
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
    "options":{"useKnowledge":true,"useBusinessData":true,"useTools":true,"topK":5}
  }')"

echo "$RESP" | python3 -m json.tool | sed -n '1,260p'

python3 - "$RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
trace=data.get("trace") or payload.get("trace") or {}
steps=trace.get("steps") or []
llm=[s for s in steps if s.get("name")=="llm_answer"]
if not llm:
    raise SystemExit("[FAIL] trace 中没有 llm_answer")
step=llm[0]
status=step.get("status")
print("[INFO] llm_answer status =", status, "data =", step.get("data"))
if status == "SKIPPED":
    d=step.get("data") or {}
    if d.get("enabled") is True:
        raise SystemExit("[FAIL] LLM 已启用但仍显示 SKIPPED，应显示 FAILED 并给出错误原因")
    print("[OK] LLM 未启用，SKIPPED 合理；请配置 srmp.llm.base-url/api-key/model 后再验证")
else:
    print("[OK] LLM step 不再误显示 SKIPPED：", status)
PY
