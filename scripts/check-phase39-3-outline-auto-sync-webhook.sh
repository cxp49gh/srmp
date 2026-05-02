#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
SECRET="${SECRET:-phase39-3-secret}"
INTERVAL="${INTERVAL:-60}"
VECTOR_LIMIT="${VECTOR_LIMIT:-200}"

echo "==> 创建自动同步配置"
CFG="$(curl -fsS -X POST "$BASE_URL/api/outline/auto-sync/configs" -H "Content-Type: application/json" -d "{
  \"name\":\"Phase39.3 自动同步验收\",
  \"enabled\":true,
  \"intervalMinutes\":$INTERVAL,
  \"force\":false,
  \"cleanupMissing\":false,
  \"vectorizeAfterSync\":true,
  \"vectorForce\":false,
  \"vectorLimit\":$VECTOR_LIMIT,
  \"webhookEnabled\":true,
  \"webhookSecret\":\"$SECRET\"
}")"
echo "$CFG" | python3 -m json.tool | sed -n '1,220p'
CONFIG_ID="$(python3 - "$CFG" <<'PY'
import json,sys
p=json.loads(sys.argv[1]); d=p.get("data") or p; print(d.get("id",""))
PY
)"
[ -n "$CONFIG_ID" ] || { echo "[FAIL] 未返回 config id"; exit 1; }

echo "==> 手动运行配置：$CONFIG_ID"
RUN="$(curl -fsS -X POST "$BASE_URL/api/outline/auto-sync/configs/$CONFIG_ID/run" -H "Content-Type: application/json" -d '{"triggerType":"MANUAL"}')"
echo "$RUN" | python3 -m json.tool | sed -n '1,260p'

echo "==> 模拟 webhook document.updated"
WEBHOOK="$(curl -fsS -X POST "$BASE_URL/api/outline/auto-sync/webhook" -H "Content-Type: application/json" -H "X-Outline-Webhook-Secret: $SECRET" -d '{"event":"document.updated","documentId":"phase39-3-non-existing-doc","collectionId":null}')"
echo "$WEBHOOK" | python3 -m json.tool | sed -n '1,240p'

echo "==> 模拟 webhook document.deleted"
curl -fsS -X POST "$BASE_URL/api/outline/auto-sync/webhook" -H "Content-Type: application/json" -H "X-Outline-Webhook-Secret: $SECRET" -d '{"event":"document.deleted","documentId":"phase39-3-non-existing-doc"}' | python3 -m json.tool | sed -n '1,180p'

echo "==> 查询运行记录"
curl -fsS "$BASE_URL/api/outline/auto-sync/runs?configId=$CONFIG_ID&limit=20" | python3 -m json.tool | sed -n '1,260p'

echo "==> 扫描 due config"
curl -fsS -X POST "$BASE_URL/api/outline/auto-sync/scan-due" | python3 -m json.tool | sed -n '1,200p'
echo "[OK] Phase39.3 Outline 自动同步与 Webhook 验收通过"
