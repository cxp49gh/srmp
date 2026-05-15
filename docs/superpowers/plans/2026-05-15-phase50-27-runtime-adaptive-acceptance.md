# Phase50.27 Runtime Adaptive Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repeatable live acceptance gate for the Phase50.26 adaptive replay compare path.

**Architecture:** A standalone Bash script checks already-running Java, Runtime, and frontend services. The script writes diagnostics to `/tmp`, uses embedded Python for JSON assertions, and keeps Phase50.26 static checks separate. A short operator document explains prerequisites, commands, and common failures.

**Tech Stack:** Bash, curl, Python JSON parsing, Docker-hosted SRMP Java backend, FastAPI Runtime, nginx-hosted Vite frontend.

---

## File Structure

- Create `scripts/check-phase50-27-runtime-adaptive-acceptance.sh`: live HTTP acceptance script for health, config, map-agent run, Java ops compare replay, and frontend asset checks.
- Create `docs/phase50_27_runtime_adaptive_acceptance.md`: operator-facing instructions and troubleshooting notes.

## Task 1: Live Acceptance Script

**Files:**
- Create: `scripts/check-phase50-27-runtime-adaptive-acceptance.sh`

- [ ] **Step 1: Create the script with strict mode and helpers**

Create `scripts/check-phase50-27-runtime-adaptive-acceptance.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

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

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing command: $1"
}

curl_json() {
  local method="$1"
  local url="$2"
  local output="$3"
  local timeout="$4"
  local body="${5:-}"
  if [ "$method" = "GET" ]; then
    curl -fsS --max-time "$timeout" "$url" > "$output" || fail "GET failed: $url; response: $output"
  else
    curl -fsS --max-time "$timeout" -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: $TENANT_ID" \
      --data @"$body" > "$output" || fail "$method failed: $url; response: $output"
  fi
}
```

- [ ] **Step 2: Add embedded JSON assertion helper**

Append this helper:

```bash
assert_json() {
  local file="$1"
  local label="$2"
  local code="$3"
  PYTHONIOENCODING=utf-8 python3 - "$file" "$label" "$code" <<'PY'
import json
import sys

path, label, code = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, encoding="utf-8") as fh:
    obj = json.load(fh)

ns = {"obj": obj}
safe_builtins = {
    "all": all,
    "any": any,
    "bool": bool,
    "int": int,
    "len": len,
    "str": str,
}
try:
    ok = bool(eval(code, {"__builtins__": safe_builtins}, ns))
except Exception as exc:
    print(f"[FAIL] {label}: {exc}; file={path}", file=sys.stderr)
    sys.exit(1)

if not ok:
    preview = json.dumps(obj, ensure_ascii=False)[:1200]
    print(f"[FAIL] {label}; file={path}; preview={preview}", file=sys.stderr)
    sys.exit(1)
print(f"[OK] {label}")
PY
}
```

- [ ] **Step 3: Add health and config checks**

Append:

```bash
require_cmd curl
require_cmd python3

RUNTIME_HEALTH="$TMP_PREFIX-runtime-health.json"
JAVA_HEALTH="$TMP_PREFIX-java-health.json"
FRONTEND_INDEX="$TMP_PREFIX-frontend-index.html"
RUNTIME_CONFIG="$TMP_PREFIX-runtime-config.json"
JAVA_CONFIG="$TMP_PREFIX-java-config.json"

curl_json GET "$RUNTIME_URL/health" "$RUNTIME_HEALTH" "$HEALTH_TIMEOUT"
assert_json "$RUNTIME_HEALTH" "Runtime health is UP" "obj.get('status') == 'UP'"
assert_json "$RUNTIME_HEALTH" "Runtime strategy includes adaptive nodes" "'adaptive_tool_planning' in ((obj.get('strategy') or {}).get('nodeFlow') or [])"

curl_json GET "$JAVA_URL/api/agent/orchestrator/health" "$JAVA_HEALTH" "$HEALTH_TIMEOUT"
assert_json "$JAVA_HEALTH" "Java orchestrator health is UP" "obj.get('code') == 0 and (obj.get('data') or {}).get('status') == 'UP'"
assert_json "$JAVA_HEALTH" "Java health sees Runtime" "(((obj.get('data') or {}).get('langgraphHealth') or {}).get('ok')) is True"

curl -fsS --max-time "$HEALTH_TIMEOUT" "$FRONTEND_URL/" > "$FRONTEND_INDEX" || fail "Frontend index failed: $FRONTEND_URL; response: $FRONTEND_INDEX"
pass "Frontend index is reachable"

curl_json GET "$RUNTIME_URL/api/srmp/langgraph/runtime/config" "$RUNTIME_CONFIG" "$HEALTH_TIMEOUT"
assert_json "$RUNTIME_CONFIG" "Runtime exposes adaptive added-tool budget" "(obj.get('safeConfig') or {}).get('adaptivePlanningEnabled') is True and (obj.get('safeConfig') or {}).get('maxAdaptiveAddedTools') == 1"

curl_json GET "$JAVA_URL/api/agent/orchestrator/ops/config" "$JAVA_CONFIG" "$HEALTH_TIMEOUT"
assert_json "$JAVA_CONFIG" "Java ops proxies adaptive budget config" "obj.get('code') == 0 and ((obj.get('data') or {}).get('ok')) is True and ((((obj.get('data') or {}).get('body') or {}).get('safeConfig') or {}).get('maxAdaptiveAddedTools')) == 1"
```

- [ ] **Step 4: Add map-agent run request and assertions**

Append:

```bash
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
assert_json "$RUN_RESPONSE" "Java map-agent run succeeds" "obj.get('code') == 0"
assert_json "$RUN_RESPONSE" "Run keeps expected action and intent" "((obj.get('data') or {}).get('action')) == 'ANALYZE_REGION' and ((obj.get('data') or {}).get('intent')) == 'REGION_ANALYSIS'"
assert_json "$RUN_RESPONSE" "Run returns Runtime audit id" "bool((((obj.get('data') or {}).get('data') or {}).get('runtimeAuditId')))"
assert_json "$RUN_RESPONSE" "Run executes adaptive knowledge tool" "(((obj.get('data') or {}).get('data') or {}).get('adaptivePlanning') or {}).get('status') == 'EXECUTED' and 'knowledge.retrieve' in ((((obj.get('data') or {}).get('data') or {}).get('adaptivePlanning') or {}).get('addedToolNames') or [])"
assert_json "$RUN_RESPONSE" "Run executes at least two tools" "int((((obj.get('data') or {}).get('data') or {}).get('toolTotalCount')) or 0) >= 2"

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
```

- [ ] **Step 5: Add Java ops compare replay assertions**

Append:

```bash
COMPARE_RESPONSE="$TMP_PREFIX-compare-response.json"
EMPTY_BODY="$TMP_PREFIX-empty.json"
printf '{}' > "$EMPTY_BODY"
curl_json POST "$JAVA_URL/api/agent/orchestrator/ops/replay/${AUDIT_ID}?execute=true&adaptiveMode=compare" "$COMPARE_RESPONSE" "$REPLAY_TIMEOUT" "$EMPTY_BODY"

assert_json "$COMPARE_RESPONSE" "Java ops compare request succeeds" "obj.get('code') == 0 and ((obj.get('data') or {}).get('ok')) is True"
assert_json "$COMPARE_RESPONSE" "Runtime compare payload is present" "(((obj.get('data') or {}).get('body') or {}).get('execute')) is True and (((obj.get('data') or {}).get('body') or {}).get('adaptiveMode')) == 'compare'"
assert_json "$COMPARE_RESPONSE" "Compare includes baseline and adaptive responses" "bool(((((obj.get('data') or {}).get('body') or {}).get('baseline') or {}).get('response')) and bool(((((obj.get('data') or {}).get('body') or {}).get('adaptive') or {}).get('response'))"
assert_json "$COMPARE_RESPONSE" "Compare status shows adaptive execution" "((((obj.get('data') or {}).get('body') or {}).get('compare') or {}).get('baselineAdaptiveStatus')) == 'DISABLED' and ((((obj.get('data') or {}).get('body') or {}).get('compare') or {}).get('adaptiveStatus')) == 'EXECUTED'"
assert_json "$COMPARE_RESPONSE" "Compare has positive tool delta" "int(((((obj.get('data') or {}).get('body') or {}).get('compare') or {}).get('toolDelta')) or 0) >= 1"
assert_json "$COMPARE_RESPONSE" "Adaptive replay executes more tools than baseline" "int(((((((obj.get('data') or {}).get('body') or {}).get('adaptive') or {}).get('response') or {}).get('data') or {}).get('toolTotalCount')) or 0) > int(((((((obj.get('data') or {}).get('body') or {}).get('baseline') or {}).get('response') or {}).get('data') or {}).get('toolTotalCount')) or 0)"

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
```

- [ ] **Step 6: Add frontend asset assertions and final summary**

Append:

```bash
FRONTEND_JS_PATH="$(sed -n 's/.*src="\([^"]*index-[^"]*\.js\)".*/\1/p' "$FRONTEND_INDEX" | head -1)"
[ -n "$FRONTEND_JS_PATH" ] || fail "Could not find Vite index JS in $FRONTEND_INDEX"
FRONTEND_JS="$TMP_PREFIX-frontend-index.js"
curl -fsS --max-time "$HEALTH_TIMEOUT" "$FRONTEND_URL$FRONTEND_JS_PATH" > "$FRONTEND_JS" || fail "Frontend JS download failed: $FRONTEND_URL$FRONTEND_JS_PATH"
grep -q '自适应对比' "$FRONTEND_JS" || fail "Frontend JS does not contain 自适应对比: $FRONTEND_JS"
grep -q 'adaptiveMode' "$FRONTEND_JS" || fail "Frontend JS does not contain adaptiveMode: $FRONTEND_JS"
pass "Frontend artifact contains adaptive compare entry"

cat <<INFO
[OK] Phase50.27 runtime adaptive acceptance passed
      runtimeAuditId=$AUDIT_ID
      traceId=$TRACE_ID
      diagnosticsPrefix=$TMP_PREFIX
INFO
```

- [ ] **Step 7: Make the script executable and run syntax check**

Run:

```bash
chmod +x scripts/check-phase50-27-runtime-adaptive-acceptance.sh
bash -n scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

Expected: no output from `bash -n`.

## Task 2: Operator Documentation

**Files:**
- Create: `docs/phase50_27_runtime_adaptive_acceptance.md`

- [ ] **Step 1: Create the documentation**

Create `docs/phase50_27_runtime_adaptive_acceptance.md`:

```markdown
# Phase50.27 运行时自适应验收

Phase50.27 不新增运行时能力，而是把 Phase50.26 的真实链路验收固化为 live acceptance gate。

## 前提

服务必须已经启动，并且运行当前代码：

- Java backend: `http://127.0.0.1:8080`
- LangGraph Runtime: `http://127.0.0.1:18080`
- Frontend: `http://127.0.0.1:5173`

该脚本不会启动、停止、重建容器，也不会重置数据库。

## 命令

```bash
bash scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

可覆盖地址：

```bash
JAVA_URL=http://127.0.0.1:8080 \
RUNTIME_URL=http://127.0.0.1:18080 \
FRONTEND_URL=http://127.0.0.1:5173 \
TENANT_ID=default \
bash scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

## 验收内容

- Runtime health 为 `UP`。
- Java orchestrator health 为 `UP`，且能访问 Runtime。
- Runtime 和 Java ops config 都暴露 `maxAdaptiveAddedTools=1`。
- Java `map-agent/run` 能产生 `runtimeAuditId`。
- 该请求触发自适应追加 `knowledge.retrieve`。
- Java ops replay `adaptiveMode=compare` 返回 baseline/adaptive/compare。
- compare 中 baseline 为 `DISABLED`，adaptive 为 `EXECUTED`，且 `toolDelta >= 1`。
- 前端产物包含“自适应对比”和 `adaptiveMode`。

## 诊断文件

脚本会把响应写到 `/tmp/srmp-phase50-27-*`。失败时优先查看对应 JSON 或 HTML/JS 文件。

## 常见失败

- 运行中容器未更新到当前 `main`：重建并重启 `backend`、`srmp-ai-orchestrator`、`frontend`。
- Runtime config 没有 `maxAdaptiveAddedTools`：说明 Runtime 镜像不是 Phase50.26 之后的代码。
- Java `map-agent/run` 返回 Runtime DTO 反序列化错误：检查 `MapAgentRunResponse` 是否接收 `planExecution`。
- `evidenceImproved=false`：这不是失败条件，本地知识库 0 命中时可能出现，但 compare 结构和工具差值仍然有效。
```

- [ ] **Step 2: Verify documentation has no placeholders**

Run:

```bash
rg -n "TO""DO|TB""D|待""定|FIX""ME" docs/phase50_27_runtime_adaptive_acceptance.md docs/superpowers/plans/2026-05-15-phase50-27-runtime-adaptive-acceptance.md
```

Expected: no matches.

## Task 3: Verification and Commit

**Files:**
- Test: `scripts/check-phase50-27-runtime-adaptive-acceptance.sh`
- Test: `scripts/check-phase50-26-adaptive-replay-guard.sh`

- [ ] **Step 1: Run live acceptance**

Run:

```bash
bash scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

Expected: final line contains `Phase50.27 runtime adaptive acceptance passed`.

- [ ] **Step 2: Run static regression gate**

Run:

```bash
bash scripts/check-phase50-26-adaptive-replay-guard.sh
```

Expected: final line contains `Phase50.26 adaptive replay guard checks passed`.

- [ ] **Step 3: Run diff hygiene**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only intended Phase50.27 files modified or created.

- [ ] **Step 4: Commit implementation**

Run:

```bash
git add scripts/check-phase50-27-runtime-adaptive-acceptance.sh docs/phase50_27_runtime_adaptive_acceptance.md docs/superpowers/plans/2026-05-15-phase50-27-runtime-adaptive-acceptance.md
git commit -m "test: add phase50 runtime adaptive acceptance"
```

Expected: commit succeeds.

## Self-Review

- Spec coverage: health, config, Java run, Java ops compare, frontend asset, documentation, and diagnostics are covered by tasks.
- Placeholder scan: the plan does not contain unresolved placeholder keywords.
- Type consistency: field names match the Phase50.27 design: `runtimeAuditId`, `adaptivePlanning`, `maxAdaptiveAddedTools`, `adaptiveMode`, `baseline`, `adaptive`, and `compare`.
