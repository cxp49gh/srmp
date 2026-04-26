#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少命令：$1"
    exit 1
  }
}

require_cmd curl
require_cmd python3

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"

  if [ -n "$body" ]; then
    curl -s -X "$method" "$url"       -H "Content-Type: application/json"       -H "X-Tenant-Id: $TENANT_ID"       -d "$body"
  else
    curl -s -X "$method" "$url"       -H "X-Tenant-Id: $TENANT_ID"
  fi
}

assert_code_zero() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
payload = sys.argv[2]
try:
    data = json.loads(payload)
except Exception as exc:
    print(f"[FAIL] {name}: 返回不是 JSON: {exc}")
    print(payload)
    sys.exit(1)

if data.get("code") != 0:
    print(f"[FAIL] {name}: code != 0")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)

print(f"[OK] {name}")
PY
}

assert_features_non_empty() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
data = json.loads(sys.argv[2])
features = ((data.get("data") or {}).get("features") or [])
if not features:
    print(f"[FAIL] {name}: features 为空")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
print(f"[OK] {name}: features={len(features)}")
PY
}

assert_ai_not_null_text() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
data = json.loads(sys.argv[2])
text = json.dumps(data.get("data"), ensure_ascii=False)
bad = ["平均 MQI null", "平均 PQI null", "平均 PCI null", "总数 0"]
for item in bad:
    if item in text:
        print(f"[FAIL] {name}: 命中异常文本 {item}")
        print(text)
        sys.exit(1)
print(f"[OK] {name}")
PY
}

echo "==> 开始一期接口验收：$BASE_URL"

health="$(request GET "$BASE_URL/api/health")"
assert_code_zero "health" "$health"

routes="$(request GET "$BASE_URL/api/gis/road-routes?routeCode=G210")"
assert_code_zero "road-routes" "$routes"
assert_features_non_empty "road-routes" "$routes"

diseases="$(request GET "$BASE_URL/api/gis/diseases?routeCode=G210")"
assert_code_zero "diseases" "$diseases"
assert_features_non_empty "diseases" "$diseases"

assessments="$(request GET "$BASE_URL/api/gis/assessment-results?routeCode=G210&year=2026&indexCode=MQI")"
assert_code_zero "assessment-results" "$assessments"
assert_features_non_empty "assessment-results" "$assessments"

route_ai="$(request POST "$BASE_URL/api/agent/analyze/route" '{"routeCode":"G210","year":2026}')"
assert_code_zero "agent-analyze-route" "$route_ai"
assert_ai_not_null_text "agent-analyze-route" "$route_ai"

report="$(request POST "$BASE_URL/api/agent/report/assessment" '{"routeCode":"G210","year":2026}')"
assert_code_zero "agent-report-assessment" "$report"
assert_ai_not_null_text "agent-report-assessment" "$report"

echo "==> 一期接口验收通过"
