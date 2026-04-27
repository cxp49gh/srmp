#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] 缺少命令：$1"
    exit 1
  }
}

require_cmd curl
require_cmd python3

echo "==> 检查 AI 问答是否能读取 G210 演示数据"

RESP="$(curl -s -X POST "$BASE_URL/api/agent/chat" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{
    "message": "分析 G210 2026 年路况",
    "context": {"routeCode":"G210","year":2026},
    "options": {"useBusinessData":true,"useKnowledge":false,"useOutline":false,"topK":5}
  }')"

python3 - "$RESP" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
if payload.get("code") != 0:
    print("[FAIL] /api/agent/chat code != 0")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
data = payload.get("data") or {}
answer = data.get("answer") or payload.get("answer") or ""
text = str(answer)
if not text.strip():
    print("[FAIL] answer 为空")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
print("[OK] answer length=", len(text))
print(text[:500])
PY