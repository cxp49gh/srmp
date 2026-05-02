#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-50}"
FORCE="${FORCE:-false}"
VECTOR_LIMIT="${VECTOR_LIMIT:-200}"

echo "==> 1. Outline knowledge stats before"
curl -fsS "$BASE_URL/api/outline/knowledge-stats" | python3 -m json.tool | sed -n '1,180p'

echo "==> 2. Dry Run should not write knowledge"
DRY_BODY="{\"limit\":$LIMIT,\"force\":$FORCE,\"dryRun\":true,\"cleanupMissing\":false}"
DRY="$(curl -fsS -X POST "$BASE_URL/api/outline/sync" -H "Content-Type: application/json" -d "$DRY_BODY")"
echo "$DRY" | python3 -m json.tool | sed -n '1,220p'
DRY_ID="$(python3 -c 'import json,sys; p=json.loads(sys.argv[1]); d=p.get("data") or p; print(d.get("id", ""))' "$DRY")"
[ -n "$DRY_ID" ] || { echo "[FAIL] dryRun 没有返回 task id"; exit 1; }

DETAILS="$(curl -fsS "$BASE_URL/api/outline/sync-tasks/$DRY_ID/details?limit=1000")"
echo "$DETAILS" | python3 -m json.tool | sed -n '1,220p'
python3 -c 'import json,sys; task=(json.loads(sys.argv[1]).get("data") or json.loads(sys.argv[1])); items=(json.loads(sys.argv[2]).get("data") or json.loads(sys.argv[2]));
assert task.get("status") in ("DRY_RUN","DRY_RUN_PARTIAL"), "dryRun status error: "+str(task.get("status"));
assert not any(x.get("status")=="SUCCESS" for x in items), "dryRun details should not contain SUCCESS";
print("[OK] dryRun + details contract passed")' "$DRY" "$DETAILS"

echo "==> 3. Details status filter should work"
FAILED_DETAILS="$(curl -fsS "$BASE_URL/api/outline/sync-tasks/$DRY_ID/details?status=FAILED&limit=1000")"
echo "$FAILED_DETAILS" | python3 -m json.tool | sed -n '1,220p'
python3 -c 'import json,sys; d=json.loads(sys.argv[1]); items=d.get("data",[]); assert isinstance(items,list), "data is not a list"; assert not any(x.get("status")!="FAILED" for x in items), "status filter failed"; print("[OK] status filter works, failed count=", len(items))' "$FAILED_DETAILS"

echo "==> 4. Real sync"
SYNC_BODY="{\"limit\":$LIMIT,\"force\":$FORCE,\"dryRun\":false,\"cleanupMissing\":false}"
SYNC="$(curl -fsS -X POST "$BASE_URL/api/outline/sync" -H "Content-Type: application/json" -d "$SYNC_BODY")"
echo "$SYNC" | python3 -m json.tool | sed -n '1,220p'
SYNC_ID="$(python3 -c 'import json,sys; p=json.loads(sys.argv[1]); d=p.get("data") or p; print(d.get("id", ""))' "$SYNC")"
python3 -c 'import json,sys; d=(json.loads(sys.argv[1]).get("data") or json.loads(sys.argv[1])); failed=int(d.get("fail_count") or d.get("failCount") or 0); status=d.get("status"); assert not (failed>0 and status=="SUCCESS"), "fail_count > 0 but status=SUCCESS"; assert status in ("SUCCESS","PARTIAL_SUCCESS","FAILED"), "real sync status error: "+str(status); print("[OK] real sync status contract passed")' "$SYNC"

echo "==> 5. Retry failed endpoint contract"
curl -fsS -X POST "$BASE_URL/api/outline/sync-tasks/$SYNC_ID/retry-failed" -H "Content-Type: application/json" -d '{"force":true}' | python3 -m json.tool | sed -n '1,220p'

echo "==> 6. Vectorize OUTLINE chunks"
curl -fsS -X POST "$BASE_URL/api/outline/vectorize" -H "Content-Type: application/json" -d "{\"force\":false,\"dryRun\":false,\"limit\":$VECTOR_LIMIT}" | python3 -m json.tool | sed -n '1,220p'

echo "==> 7. Outline knowledge stats after"
STATS_AFTER="$(curl -fsS "$BASE_URL/api/outline/knowledge-stats")"
echo "$STATS_AFTER" | python3 -m json.tool | sed -n '1,220p'
python3 -c 'import json,sys; d=(json.loads(sys.argv[1]).get("data") or json.loads(sys.argv[1])); missing=[k for k in ["documentCount","chunkCount","embeddedChunkCount","pendingEmbeddingChunkCount"] if k not in d]; assert not missing, "stats missing: "+str(missing); print("[OK] stats contract passed")' "$STATS_AFTER"

echo "[OK] Phase39.2.2 Outline sync contract + vector closure passed"
