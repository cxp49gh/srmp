#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
ALLOW_MOCK="${ALLOW_MOCK:-false}"

STATS="$(curl -fsS "$BASE_URL/api/ai/knowledge/stats?tenantId=$TENANT_ID")"
echo "$STATS" | python3 -m json.tool

python3 - "$STATS" "$ALLOW_MOCK" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
allow_mock = sys.argv[2].lower() == "true"
data = payload.get("data") or payload
chunk = int(data.get("chunkCount") or 0)
embedded = int(data.get("embeddedChunkCount") or 0)
provider = str(data.get("embeddingProvider") or "")
providers = data.get("chunkEmbeddingProviders") or {}

if chunk <= 0:
    raise SystemExit("[FAIL] chunkCount=0，请先导入知识")
if embedded != chunk:
    raise SystemExit(f"[FAIL] embeddedChunkCount({embedded}) != chunkCount({chunk})，请执行 reindex")
if not data.get("vectorEnabled"):
    raise SystemExit("[FAIL] vectorEnabled=false，请检查 pgvector")
if (not allow_mock) and provider.lower() == "mock":
    raise SystemExit("[FAIL] 当前 embeddingProvider=mock，不适合做真实质量评测")
if (not allow_mock):
    mock_keys = [k for k in providers.keys() if "mock" in str(k).lower()]
    if mock_keys:
        raise SystemExit("[FAIL] chunkEmbeddingProviders 中仍存在 mock 残留：" + ",".join(mock_keys))
if not providers:
    raise SystemExit("[FAIL] chunkEmbeddingProviders 为空，请确认 phase37_1 migration 是否执行")
print("[OK] embedding 一致性检查通过")
PY
