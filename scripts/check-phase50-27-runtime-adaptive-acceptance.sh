#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

JAVA_URL="${JAVA_URL:-http://127.0.0.1:8080}"
RUNTIME_URL="${RUNTIME_URL:-http://127.0.0.1:18080}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:5173}"
TENANT_ID="${TENANT_ID:-default}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-10}"
RUN_TIMEOUT="${RUN_TIMEOUT:-180}"
REPLAY_TIMEOUT="${REPLAY_TIMEOUT:-240}"
TMP_PREFIX="${TMP_PREFIX:-/tmp/srmp-phase50-27}"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

pass() {
  echo "[OK] $*"
}

info() {
  echo "[INFO] $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing command: $1"
}

curl_json() {
  local method="$1"
  local url="$2"
  local output="$3"
  local timeout="$4"
  local body="${5:-}"
  local http_status

  if [ "$method" = "GET" ]; then
    http_status="$(curl -sS --max-time "$timeout" -o "$output" -w "%{http_code}" "$url")" \
      || fail "GET failed: $url; diagnostics: $output"
  else
    [ -n "$body" ] || fail "$method requires a JSON body file: $url"
    http_status="$(curl -sS --max-time "$timeout" -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: $TENANT_ID" \
      --data @"$body" \
      -o "$output" -w "%{http_code}")" \
      || fail "$method failed: $url; diagnostics: $output"
  fi

  case "$http_status" in
    2??) ;;
    *) fail "$method returned HTTP $http_status: $url; diagnostics: $output" ;;
  esac
}

assert_json() {
  local file="$1"
  local label="$2"
  local code="$3"
  PYTHONIOENCODING=utf-8 python3 - "$file" "$label" "$code" <<'PY'
import json
import sys


def path(value, *keys, default=None):
    current = value
    for key in keys:
        if not isinstance(current, dict):
            return default
        current = current.get(key)
        if current is None:
            return default
    return current


json_path, label, code = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    with open(json_path, encoding="utf-8") as fh:
        obj = json.load(fh)
except Exception as exc:
    print(f"[FAIL] {label}: invalid JSON: {exc}; file={json_path}", file=sys.stderr)
    sys.exit(1)

safe_builtins = {
    "all": all,
    "any": any,
    "bool": bool,
    "int": int,
    "len": len,
    "str": str,
}
namespace = {"obj": obj, "path": path}
try:
    ok = bool(eval(code, {"__builtins__": safe_builtins}, namespace))
except Exception as exc:
    print(f"[FAIL] {label}: {exc}; file={json_path}", file=sys.stderr)
    sys.exit(1)

if not ok:
    preview = json.dumps(obj, ensure_ascii=False)[:1200]
    print(f"[FAIL] {label}; file={json_path}; preview={preview}", file=sys.stderr)
    sys.exit(1)
print(f"[OK] {label}")
PY
}

require_cmd curl
require_cmd grep
require_cmd python3
require_cmd sed

mkdir -p "$(dirname "$TMP_PREFIX")"

info "Java=$JAVA_URL Runtime=$RUNTIME_URL Frontend=$FRONTEND_URL Tenant=$TENANT_ID"

RUNTIME_HEALTH="$TMP_PREFIX-runtime-health.json"
JAVA_HEALTH="$TMP_PREFIX-java-health.json"
FRONTEND_INDEX="$TMP_PREFIX-frontend-index.html"
RUNTIME_CONFIG="$TMP_PREFIX-runtime-config.json"
JAVA_CONFIG="$TMP_PREFIX-java-config.json"

curl_json GET "$RUNTIME_URL/health" "$RUNTIME_HEALTH" "$HEALTH_TIMEOUT"
assert_json "$RUNTIME_HEALTH" "Runtime health is UP" "path(obj, 'status') == 'UP'"
assert_json "$RUNTIME_HEALTH" "Runtime strategy includes adaptive nodes" "'adaptive_tool_planning' in (path(obj, 'strategy', 'nodeFlow', default=[]) or [])"

curl_json GET "$JAVA_URL/api/agent/orchestrator/health" "$JAVA_HEALTH" "$HEALTH_TIMEOUT"
assert_json "$JAVA_HEALTH" "Java orchestrator health is UP" "path(obj, 'code') == 0 and path(obj, 'data', 'status') == 'UP'"
assert_json "$JAVA_HEALTH" "Java health sees Runtime" "path(obj, 'data', 'langgraphHealth', 'ok') is True"

FRONTEND_STATUS="$(curl -sS --max-time "$HEALTH_TIMEOUT" -o "$FRONTEND_INDEX" -w "%{http_code}" "$FRONTEND_URL/")" \
  || fail "Frontend index failed: $FRONTEND_URL; diagnostics: $FRONTEND_INDEX"
case "$FRONTEND_STATUS" in
  2??) pass "Frontend index is reachable" ;;
  *) fail "Frontend index returned HTTP $FRONTEND_STATUS: $FRONTEND_URL; diagnostics: $FRONTEND_INDEX" ;;
esac

curl_json GET "$RUNTIME_URL/api/srmp/langgraph/runtime/config" "$RUNTIME_CONFIG" "$HEALTH_TIMEOUT"
assert_json "$RUNTIME_CONFIG" "Runtime exposes adaptive added-tool budget" "path(obj, 'safeConfig', 'adaptivePlanningEnabled') is True and path(obj, 'safeConfig', 'maxAdaptiveAddedTools') == 1"

curl_json GET "$JAVA_URL/api/agent/orchestrator/ops/config" "$JAVA_CONFIG" "$HEALTH_TIMEOUT"
assert_json "$JAVA_CONFIG" "Java ops proxies adaptive budget config" "path(obj, 'code') == 0 and path(obj, 'data', 'ok') is True and path(obj, 'data', 'body', 'safeConfig', 'maxAdaptiveAddedTools') == 1"

TRACE_ID="phase50-27-runtime-$(date +%Y%m%d%H%M%S)"
RUN_REQUEST="$TMP_PREFIX-run-request.json"
RUN_RESPONSE="$TMP_PREFIX-run-response.json"

cat > "$RUN_REQUEST" <<JSON
{
  "action": "ANALYZE_REGION",
  "message": "请分析一段没有示例数据的路线区域，并补充养护处置依据。",
  "mapContext": {
    "tenantId": "$TENANT_ID",
    "mode": "REGION",
    "routeCode": "NO_DATA_PHASE50_27",
    "year": 2026,
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[106.0, 30.0], [106.01, 30.0], [106.01, 30.01], [106.0, 30.01], [106.0, 30.0]]]
    }
  },
  "options": {
    "traceId": "$TRACE_ID",
    "useKnowledge": false,
    "topK": 3,
    "maxAdaptiveAddedTools": 1
  }
}
JSON

curl_json POST "$JAVA_URL/api/agent/map-agent/run" "$RUN_RESPONSE" "$RUN_TIMEOUT" "$RUN_REQUEST"
assert_json "$RUN_RESPONSE" "Java map-agent run succeeds" "path(obj, 'code') == 0"
assert_json "$RUN_RESPONSE" "Run keeps expected action and intent" "path(obj, 'data', 'action') == 'ANALYZE_REGION' and path(obj, 'data', 'intent') == 'REGION_ANALYSIS'"
assert_json "$RUN_RESPONSE" "Run returns Runtime audit id" "bool(path(obj, 'data', 'data', 'runtimeAuditId'))"
assert_json "$RUN_RESPONSE" "Run executes adaptive knowledge tool" "path(obj, 'data', 'data', 'adaptivePlanning', 'status') == 'EXECUTED' and 'knowledge.retrieve' in (path(obj, 'data', 'data', 'adaptivePlanning', 'addedToolNames', default=[]) or [])"
assert_json "$RUN_RESPONSE" "Run executes at least two tools" "int(path(obj, 'data', 'data', 'toolTotalCount', default=0) or 0) >= 2"

AUDIT_ID="$(PYTHONIOENCODING=utf-8 python3 - "$RUN_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    obj = json.load(fh)
print((((obj.get("data") or {}).get("data") or {}).get("runtimeAuditId")) or "")
PY
)"
[ -n "$AUDIT_ID" ] || fail "runtimeAuditId missing in $RUN_RESPONSE"
pass "Runtime audit id: $AUDIT_ID"

COMPARE_RESPONSE="$TMP_PREFIX-compare-response.json"
EMPTY_BODY="$TMP_PREFIX-empty.json"
printf '{}' > "$EMPTY_BODY"
curl_json POST "$JAVA_URL/api/agent/orchestrator/ops/replay/${AUDIT_ID}?execute=true&adaptiveMode=compare" "$COMPARE_RESPONSE" "$REPLAY_TIMEOUT" "$EMPTY_BODY"

assert_json "$COMPARE_RESPONSE" "Java ops compare request succeeds" "path(obj, 'code') == 0 and path(obj, 'data', 'ok') is True"
assert_json "$COMPARE_RESPONSE" "Runtime compare payload is present" "path(obj, 'data', 'body', 'execute') is True and path(obj, 'data', 'body', 'adaptiveMode') == 'compare'"
assert_json "$COMPARE_RESPONSE" "Compare includes baseline and adaptive responses" "bool(path(obj, 'data', 'body', 'baseline', 'response')) and bool(path(obj, 'data', 'body', 'adaptive', 'response'))"
assert_json "$COMPARE_RESPONSE" "Compare status shows adaptive execution" "path(obj, 'data', 'body', 'compare', 'baselineAdaptiveStatus') == 'DISABLED' and path(obj, 'data', 'body', 'compare', 'adaptiveStatus') == 'EXECUTED'"
assert_json "$COMPARE_RESPONSE" "Compare has positive tool delta" "int(path(obj, 'data', 'body', 'compare', 'toolDelta', default=0) or 0) >= 1"
assert_json "$COMPARE_RESPONSE" "Adaptive replay executes more tools than baseline" "int(path(obj, 'data', 'body', 'adaptive', 'response', 'data', 'toolTotalCount', default=0) or 0) > int(path(obj, 'data', 'body', 'baseline', 'response', 'data', 'toolTotalCount', default=0) or 0)"

PYTHONIOENCODING=utf-8 python3 - "$COMPARE_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    obj = json.load(fh)
body = ((obj.get("data") or {}).get("body") or {})
compare = body.get("compare") or {}
print("[INFO] compare.evidenceImproved=%s costDeltaMs=%s baselineKnowledgeHitCount=%s adaptiveKnowledgeHitCount=%s" % (
    compare.get("evidenceImproved"),
    compare.get("costDeltaMs"),
    compare.get("baselineKnowledgeHitCount"),
    compare.get("adaptiveKnowledgeHitCount"),
))
PY

FRONTEND_JS_PATH="$(sed -n 's/.*src="\([^"]*index-[^"]*\.js\)".*/\1/p' "$FRONTEND_INDEX" | head -1)"
if [ -z "$FRONTEND_JS_PATH" ]; then
  sed -n '1,20p' "$FRONTEND_INDEX" >&2
  fail "Could not find Vite index JS in $FRONTEND_INDEX"
fi

FRONTEND_JS_URL="$FRONTEND_JS_PATH"
case "$FRONTEND_JS_URL" in
  http://*|https://*) ;;
  *) FRONTEND_JS_URL="${FRONTEND_URL%/}/${FRONTEND_JS_PATH#/}" ;;
esac

FRONTEND_JS="$TMP_PREFIX-frontend-index.js"
FRONTEND_JS_STATUS="$(curl -sS --max-time "$HEALTH_TIMEOUT" -o "$FRONTEND_JS" -w "%{http_code}" "$FRONTEND_JS_URL")" \
  || fail "Frontend JS download failed: $FRONTEND_JS_URL; diagnostics: $FRONTEND_JS"
case "$FRONTEND_JS_STATUS" in
  2??) ;;
  *) fail "Frontend JS returned HTTP $FRONTEND_JS_STATUS: $FRONTEND_JS_URL; diagnostics: $FRONTEND_JS" ;;
esac
grep -q '自适应对比' "$FRONTEND_JS" || fail "Frontend JS does not contain 自适应对比: $FRONTEND_JS"
grep -q 'adaptiveMode' "$FRONTEND_JS" || fail "Frontend JS does not contain adaptiveMode: $FRONTEND_JS"
pass "Frontend artifact contains adaptive compare entry"

cat <<INFO
[OK] Phase50.27 runtime adaptive acceptance passed
      runtimeAuditId=$AUDIT_ID
      traceId=$TRACE_ID
      diagnosticsPrefix=$TMP_PREFIX
INFO
