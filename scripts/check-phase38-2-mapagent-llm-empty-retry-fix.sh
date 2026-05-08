#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> LLM health probe"
curl -fsS "$BASE_URL/api/ai/llm/health?probe=true" | python3 -m json.tool

echo "==> MapAgent LLM empty retry check"
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

echo "$RESP" | python3 -m json.tool | sed -n '1,280p'

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
d=step.get("data") or {}
print("[INFO] llm_answer status =", status)
print("[INFO] llm_answer data =", json.dumps(d, ensure_ascii=False)[:2000])

if status == "SUCCESS":
    print("[OK] LLM 回答成功")
elif status == "FAILED":
    if "firstAttempt" not in d:
        raise SystemExit("[FAIL] FAILED 但缺少 firstAttempt 诊断")
    if "rawResponsePreview" not in d and "rawResponsePreview" not in (d.get("firstAttempt") or {}):
        raise SystemExit("[FAIL] FAILED 但缺少 rawResponsePreview 诊断")
    print("[WARN] LLM 仍失败，但已有完整诊断，可查看 errorMessage/finishReason/rawResponsePreview")
elif status == "SKIPPED":
    if d.get("enabled") is True:
        raise SystemExit("[FAIL] LLM enabled=true 不应显示 SKIPPED")
    print("[OK] LLM 未启用，SKIPPED 合理")
else:
    raise SystemExit("[FAIL] 未知 llm_answer status: " + str(status))
PY
