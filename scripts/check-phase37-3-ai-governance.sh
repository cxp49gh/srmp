#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> 检查敏感配置"
bash scripts/check-sensitive-config.sh

echo "==> 检查 Embedding Health"
curl -fsS "$BASE_URL/api/ai/embedding/health" | python3 -m json.tool

echo "==> 检查 RAG default cases 是否包含 expectedKeywordGroups"
cases="$(curl -fsS "$BASE_URL/api/ai/eval/rag/default-cases")"
echo "$cases" | python3 -m json.tool | sed -n '1,180p'
python3 - "$cases" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
cases=payload.get("data") or payload
crack=[c for c in cases if c.get("id")=="crack"]
if not crack:
    raise SystemExit("[FAIL] default cases 缺少 crack 用例")
groups=crack[0].get("expectedKeywordGroups") or []
if not groups:
    raise SystemExit("[FAIL] crack 用例 expectedKeywordGroups 为空")
print("[OK] default cases 已资源化并包含关键词组")
PY

echo "[OK] Phase37.3 AI 治理收口检查通过"
