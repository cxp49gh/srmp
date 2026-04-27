#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

if [ -z "${TASK_ID:-}" ]; then
  echo "请指定 TASK_ID，例如："
  echo "TASK_ID=xxx ./scripts/check-ai-solution-quality.sh"
  exit 1
fi

echo "==> 执行质量校验：$TASK_ID"
curl -s -X POST "$BASE_URL/api/ai/solution/tasks/$TASK_ID/quality-check" \
  -H "X-Tenant-Id: $TENANT_ID" | python3 -m json.tool

echo ""
echo "==> 导出 Markdown 预览："
curl -s "$BASE_URL/api/ai/solution/tasks/$TASK_ID/export/markdown" \
  -H "X-Tenant-Id: $TENANT_ID" | head -80