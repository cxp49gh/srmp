#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> 旧接口 /api/knowledge/search 兼容性验证"
RESP="$(curl -fsS -X POST "$BASE_URL/api/knowledge/search" \
  -H "Content-Type: application/json" \
  -d '{"query":"PCI 指标是什么意思？","topK":5}')"

echo "$RESP" | python3 -m json.tool

python3 - "$RESP" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
data = payload.get("data") or payload
if not isinstance(data, list):
    raise SystemExit("[FAIL] 返回 data 不是列表")
if not data:
    raise SystemExit("[FAIL] /api/knowledge/search 返回为空")
text = "\n".join([(x.get("title","") + " " + x.get("heading","") + " " + x.get("content","")) for x in data])
if "PCI" not in text.upper() and "指标" not in text:
    raise SystemExit("[FAIL] 返回结果未包含 PCI/指标 相关内容")
print("[OK] /api/knowledge/search 已兼容新 AI 知识库")
PY
