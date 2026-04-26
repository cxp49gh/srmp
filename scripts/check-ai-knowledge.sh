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
    curl -s -X "$method" "$url" \
      -H "Content-Type: application/json" \
      -H "X-Tenant-Id: $TENANT_ID" \
      -d "$body"
  else
    curl -s -X "$method" "$url" \
      -H "X-Tenant-Id: $TENANT_ID"
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

assert_list_non_empty() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
data = json.loads(sys.argv[2])
items = data.get("data")
if not isinstance(items, list) or len(items) == 0:
    print(f"[FAIL] {name}: data 不是非空列表")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
print(f"[OK] {name}: count={len(items)}")
PY
}

assert_answer_non_empty() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
data = json.loads(sys.argv[2])
body = data.get("data") or {}
answer = body.get("answer")
if not answer or len(str(answer).strip()) == 0:
    print(f"[FAIL] {name}: answer 为空")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
print(f"[OK] {name}: answer length={len(str(answer))}")
PY
}

echo "==> 开始 AI 知识库验收：$BASE_URL tenant=$TENANT_ID"

knowledge_search="$(request POST "$BASE_URL/api/knowledge/search" '{"query":"PCI 指标是什么意思","topK":5}')"
assert_code_zero "knowledge-search" "$knowledge_search"
assert_list_non_empty "knowledge-search" "$knowledge_search"

knowledge_ask="$(request POST "$BASE_URL/api/knowledge/ask" '{"query":"数据导入模板怎么使用？","topK":5}')"
assert_code_zero "knowledge-ask" "$knowledge_ask"
assert_answer_non_empty "knowledge-ask" "$knowledge_ask"

agent_chat="$(request POST "$BASE_URL/api/agent/chat" '{"message":"根据知识库解释 PCI 指标，并结合 G210 2026 年情况给出建议","context":{"routeCode":"G210","year":2026,"indexCode":"PCI"},"options":{"useBusinessData":true,"useKnowledge":true,"useOutline":false,"topK":5}}')"
assert_code_zero "agent-chat-knowledge" "$agent_chat"
assert_answer_non_empty "agent-chat-knowledge" "$agent_chat"

echo "==> AI 知识库验收通过"
