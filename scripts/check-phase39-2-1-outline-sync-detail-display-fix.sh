#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-50}"

echo "==> Outline dryRun"
DRY="$(curl -fsS -X POST "$BASE_URL/api/outline/sync" \
  -H "Content-Type: application/json" \
  -d "{\"limit\":$LIMIT,\"force\":false,\"dryRun\":true}")"
echo "$DRY" | python3 -m json.tool | sed -n '1,180p'

TASK_ID="$(python3 - "$DRY" <<'PY'
import json, sys
p=json.loads(sys.argv[1])
d=p.get("data") or p
print(d.get("id",""))
PY
)"

if [ -z "$TASK_ID" ]; then
  echo "[FAIL] dryRun 未返回 taskId"
  exit 1
fi

echo "==> 查询明细：$TASK_ID"
DETAILS="$(curl -fsS "$BASE_URL/api/outline/sync-tasks/$TASK_ID/details?limit=1000")"
echo "$DETAILS" | python3 -m json.tool | sed -n '1,260p'

python3 - "$DETAILS" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
items=payload.get("data") or payload
if not items:
    raise SystemExit("[FAIL] details 为空")
sample=items[0]
required_any = [
    ("outlineDocumentId", "outline_document_id"),
    ("outlineTitle", "outline_title"),
    ("contentChars", "content_chars"),
    ("contentHash", "content_hash"),
    ("detailMessage", "detail_message"),
    ("costMs", "cost_ms"),
]
missing=[]
for camel, snake in required_any:
    if camel not in sample and snake not in sample:
        missing.append(camel + "/" + snake)
if missing:
    raise SystemExit("[FAIL] 明细缺少字段：" + ", ".join(missing))
titles=[(x.get("outlineTitle") or x.get("outline_title") or "") for x in items]
if all(not t for t in titles):
    raise SystemExit("[FAIL] 所有明细标题都为空，展示会异常")
print("[OK] 明细字段完整，兼容 camelCase/snake_case")
PY

echo "[OK] Phase39.2.1 Outline 同步明细展示修复验收通过"
