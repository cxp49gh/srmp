#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

pretty_print() {
  local json="$1"
  local limit="${2:-220}"
  local tmp
  tmp="$(mktemp)"
  printf '%s' "$json" | python3 -m json.tool > "$tmp"
  sed -n "1,${limit}p" "$tmp"
  rm -f "$tmp"
}

echo "==> GET /api/ai/knowledge/stats"
STATS="$(curl -fsS "$BASE_URL/api/ai/knowledge/stats?tenantId=$TENANT_ID")"
pretty_print "$STATS" 200 || true

python3 - "$STATS" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
data = payload.get("data") or payload
chunk = int(data.get("chunkCount") or 0)
embedded = int(data.get("embeddedChunkCount") or 0)
if chunk <= 0:
    raise SystemExit("[FAIL] ai_knowledge_chunk 为空，请先执行 scripts/import-phase36-demo-knowledge.sh")
if embedded <= 0:
    raise SystemExit("[FAIL] embedding 为空，无法验证向量检索")
print("[OK] 知识数据存在，embedding 数量：", embedded)
PY

echo "==> POST /api/ai/knowledge/search"
SEARCH="$(curl -fsS -X POST "$BASE_URL/api/ai/knowledge/search" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"query\":\"修补损坏怎么处理\",\"topK\":5,\"sourceTypes\":[\"MANUAL\"]}")"
pretty_print "$SEARCH" 220 || true

python3 - "$SEARCH" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
data = payload.get("data") or payload
hits = data.get("hits") or []
if not hits:
    raise SystemExit("[FAIL] 知识检索无命中")
print("[OK] searchMode=", data.get("searchMode"), "vectorUsed=", data.get("vectorUsed"), "hitCount=", len(hits))
PY

echo "==> POST /api/agent/map-agent/chat"
AGENT="$(curl -fsS -X POST "$BASE_URL/api/agent/map-agent/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "这个修补损坏病害怎么处理？",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "DISEASE",
        "routeCode": "G210",
        "startStake": 69.007,
        "endStake": 69.034,
        "diseaseName": "修补损坏",
        "severity": "MEDIUM"
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 5
    }
  }')"
pretty_print "$AGENT" 220 || true

python3 - "$AGENT" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
data = payload.get("data") or payload

tool_results = data.get("toolResults") or payload.get("toolResults") or []
if not any((t.get("toolName") == "knowledge.retrieve" or t.get("name") == "knowledge.retrieve") for t in tool_results if isinstance(t, dict)):
    raise SystemExit("[FAIL] toolResults 未包含 knowledge.retrieve")

sources = (
    data.get("sources")
    or data.get("knowledgeSources")
    or payload.get("sources")
    or payload.get("knowledgeSources")
    or []
)

# Phase36.1 兜底：如果 sources 未透传，从 knowledge.retrieve 的 data.hits 提取。
if not sources:
    extracted = []
    for t in tool_results:
        if not isinstance(t, dict):
            continue
        if t.get("toolName") != "knowledge.retrieve" and t.get("name") != "knowledge.retrieve":
            continue
        td = t.get("data") or {}
        hits = td.get("hits") or (td.get("data") or {}).get("hits") or []
        if isinstance(hits, list):
            extracted.extend(hits)
    sources = extracted

if not sources:
    raise SystemExit("[FAIL] Agent sources 为空，且无法从 toolResults.data.hits 兜底提取")

trace = data.get("trace") or payload.get("trace") or {}
steps = trace.get("steps") or []
if steps and not any(s.get("name") in ("knowledge_retrieve", "tool_execute") for s in steps if isinstance(s, dict)):
    raise SystemExit("[FAIL] trace 未包含 knowledge_retrieve/tool_execute")

print("[OK] Agent RAG 验收通过，sources=", len(sources))
PY

echo "[OK] Phase36 向量知识库 RAG 端到端验收通过"
