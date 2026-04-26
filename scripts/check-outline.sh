#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
OUTLINE_SYNC_LIMIT="${OUTLINE_SYNC_LIMIT:-10}"
OUTLINE_SYNC_FORCE="${OUTLINE_SYNC_FORCE:-false}"

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

assert_outline_usable() {
  local payload="$1"

  python3 - "$payload" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
body = data.get("data") or {}
if not body.get("usable"):
    print("[WARN] Outline 当前不可用，跳过 collections/sync 检查")
    print(json.dumps(body, ensure_ascii=False, indent=2))
    sys.exit(2)
print("[OK] outline-status: usable=true")
PY
}

echo "==> 检查 Outline 接入：$BASE_URL tenant=$TENANT_ID"

status="$(request GET "$BASE_URL/api/outline/status")"
assert_code_zero "outline-status" "$status"

set +e
assert_outline_usable "$status"
usable_code=$?
set -e

if [ "$usable_code" = "2" ]; then
  echo "==> Outline 未启用或 token 不可用。请配置："
  echo "    OUTLINE_ENABLED=true"
  echo "    OUTLINE_BASE_URL=http://localhost:3000"
  echo "    OUTLINE_API_TOKEN=真实 token"
  exit 0
fi

collections="$(request GET "$BASE_URL/api/outline/collections")"
assert_code_zero "outline-collections" "$collections"

documents="$(request POST "$BASE_URL/api/outline/documents/list" "{\"limit\":$OUTLINE_SYNC_LIMIT,\"offset\":0}")"
assert_code_zero "outline-documents-list" "$documents"

sync_payload="{\"limit\":$OUTLINE_SYNC_LIMIT,\"force\":$OUTLINE_SYNC_FORCE}"
sync_result="$(request POST "$BASE_URL/api/outline/sync" "$sync_payload")"
assert_code_zero "outline-sync" "$sync_result"

tasks="$(request GET "$BASE_URL/api/outline/sync-tasks?limit=10")"
assert_code_zero "outline-sync-tasks" "$tasks"

echo "==> Outline 接入验收完成"
