#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-500}"
FORCE="${FORCE:-false}"

echo "==> Outline dryRun"
DRY="$(curl -fsS -X POST "$BASE_URL/api/outline/sync" \
  -H "Content-Type: application/json" \
  -d "{\"limit\":$LIMIT,\"force\":$FORCE,\"dryRun\":true}")"
echo "$DRY" | python3 -m json.tool | sed -n '1,220p'

TASK_ID="$(python3 - "$DRY" <<'PY'
import json, sys
p=json.loads(sys.argv[1])
d=p.get("data") or p
print(d.get("id",""))
PY
)"

if [ -z "$TASK_ID" ]; then
  echo "[FAIL] dryRun 没有返回任务ID"
  exit 1
fi

echo "==> 查询 dryRun 明细：$TASK_ID"
DETAILS="$(curl -fsS "$BASE_URL/api/outline/sync-tasks/$TASK_ID/details?limit=1000")"
echo "$DETAILS" | python3 -m json.tool | sed -n '1,240p'

python3 - "$DRY" "$DETAILS" <<'PY'
import json, sys
task=(json.loads(sys.argv[1]).get("data") or json.loads(sys.argv[1]))
details=(json.loads(sys.argv[2]).get("data") or json.loads(sys.argv[2]))
if task.get("status") not in ("DRY_RUN","DRY_RUN_PARTIAL"):
    raise SystemExit("[FAIL] dryRun 状态错误: " + str(task.get("status")))
if int(task.get("total_count") or 0) > 0 and not details:
    raise SystemExit("[FAIL] 有同步文档但 details 为空")
bad_success=[d for d in details if d.get("status")=="SUCCESS"]
if bad_success:
    raise SystemExit("[FAIL] dryRun 明细不应出现 SUCCESS")
print("[OK] dryRun + details 通过")
PY

echo "==> 正式同步"
SYNC="$(curl -fsS -X POST "$BASE_URL/api/outline/sync" \
  -H "Content-Type: application/json" \
  -d "{\"limit\":$LIMIT,\"force\":$FORCE,\"dryRun\":false}")"
echo "$SYNC" | python3 -m json.tool | sed -n '1,220p'

SYNC_TASK_ID="$(python3 - "$SYNC" <<'PY'
import json, sys
p=json.loads(sys.argv[1])
d=p.get("data") or p
print(d.get("id",""))
PY
)"

echo "==> 查询正式同步明细：$SYNC_TASK_ID"
curl -fsS "$BASE_URL/api/outline/sync-tasks/$SYNC_TASK_ID/details?limit=1000" | python3 -m json.tool | sed -n '1,240p'

python3 - "$SYNC" <<'PY'
import json, sys
task=(json.loads(sys.argv[1]).get("data") or json.loads(sys.argv[1]))
status=task.get("status")
failed=int(task.get("fail_count") or 0)
if failed > 0 and status == "SUCCESS":
    raise SystemExit("[FAIL] fail_count > 0 但 status=SUCCESS")
if status not in ("SUCCESS","PARTIAL_SUCCESS","FAILED"):
    raise SystemExit("[FAIL] 正式同步状态异常: " + str(status))
print("[OK] 正式同步状态语义通过")
PY

echo "[OK] Phase39.2 Outline 同步控制台/失败重试后端验收通过"
