#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CASE_FILE="${CASE_FILE:-docs/eval/phase37-rag/road-maintenance-rag-cases.json}"
MIN_PASS_RATE="${MIN_PASS_RATE:-0.0}"
TOP_K="${TOP_K:-5}"
REQUIRE_VECTOR_USED="${REQUIRE_VECTOR_USED:-false}"

if [ ! -f "$CASE_FILE" ]; then
  echo "[FAIL] 评测用例不存在：$CASE_FILE"
  exit 1
fi

payload="$(python3 - "$CASE_FILE" "$TOP_K" "$REQUIRE_VECTOR_USED" <<'PY'
import json, sys
case_file, top_k, require_vector = sys.argv[1:]
with open(case_file, encoding='utf-8') as f:
    cases = json.load(f)
print(json.dumps({
    'cases': cases,
    'topK': int(top_k),
    'requireVectorUsed': require_vector.lower() == 'true'
}, ensure_ascii=False))
PY
)"

echo "==> POST /api/ai/eval/rag/run"
resp="$(curl -fsS --max-time 300 -X POST "$BASE_URL/api/ai/eval/rag/run" -H "Content-Type: application/json" -d "$payload")"
printf '%s' "$resp" | python3 -m json.tool

python3 - "$resp" "$MIN_PASS_RATE" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get('data') or payload
rate=float(data.get('passRate') or 0)
total=int(data.get('total') or 0)
passed=int(data.get('passed') or 0)
print(f"[INFO] total={total} passed={passed} passRate={rate:.2%}")
if total <= 0:
    raise SystemExit('[FAIL] 无评测用例')
if passed <= 0:
    raise SystemExit('[FAIL] 没有任何用例通过')
min_rate = float(sys.argv[2]) if len(sys.argv) > 1 else 0.0
if min_rate > 0 and rate < min_rate:
    raise SystemExit(f'[FAIL] passRate {rate:.2%} < 最低要求 {min_rate:.2%}')
PY
