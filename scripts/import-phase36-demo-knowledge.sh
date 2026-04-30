#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
KNOWLEDGE_DIR="${KNOWLEDGE_DIR:-docs/knowledge/road-maintenance}"
SOURCE_TYPE="${SOURCE_TYPE:-MANUAL}"

if [ ! -d "$KNOWLEDGE_DIR" ]; then
  echo "[FAIL] 知识目录不存在：$KNOWLEDGE_DIR"
  exit 1
fi

count=0
for file in "$KNOWLEDGE_DIR"/*.md; do
  [ -f "$file" ] || continue
  title="$(basename "$file" .md)"
  source_id="$(basename "$file")"
  echo "==> 导入：$title"
  python3 - "$BASE_URL" "$TENANT_ID" "$SOURCE_TYPE" "$source_id" "$title" "$file" <<'PY'
import json, sys, urllib.request
base_url, tenant_id, source_type, source_id, title, file_path = sys.argv[1:]
content = open(file_path, encoding="utf-8").read()
payload = {
    "tenantId": tenant_id,
    "title": title,
    "sourceType": source_type,
    "sourceId": source_id,
    "content": content,
    "metadata": {"phase": "36", "demo": True, "file": source_id},
}
req = urllib.request.Request(
    base_url.rstrip("/") + "/api/ai/knowledge/ingest/markdown",
    data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=60) as resp:
    print(resp.read().decode("utf-8")[:500])
PY
  count=$((count + 1))
done

echo "[OK] 导入完成，文档数：$count"

echo "==> 知识库统计"
curl -fsS "$BASE_URL/api/ai/knowledge/stats?tenantId=$TENANT_ID" | python3 -m json.tool || true

echo "==> 检索验证"
curl -fsS -X POST "$BASE_URL/api/ai/knowledge/search" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"query\":\"修补损坏怎么处理\",\"topK\":5,\"sourceTypes\":[\"$SOURCE_TYPE\"]}" | python3 -m json.tool || true
