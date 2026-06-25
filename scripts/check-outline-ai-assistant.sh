#!/usr/bin/env bash
set -euo pipefail

export PYTHONIOENCODING="${PYTHONIOENCODING:-utf-8}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
OUTLINE_SYNC_LIMIT="${OUTLINE_SYNC_LIMIT:-50}"
OUTLINE_SYNC_FORCE="${OUTLINE_SYNC_FORCE:-true}"
TOP_K="${TOP_K:-5}"
MAP_AGENT_REQUIRED="${MAP_AGENT_REQUIRED:-false}"
AI_QUESTION="${AI_QUESTION:-当前 PCI 偏低，裂缝和坑槽集中时应该如何处置？请说明知识来源上下文。}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少命令：$1"
    exit 1
  }
}

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"

  if [ -n "$body" ]; then
    curl -sS -X "$method" "$url" \
      -H "Content-Type: application/json" \
      -H "X-Tenant-Id: $TENANT_ID" \
      -d "$body"
  else
    curl -sS -X "$method" "$url" \
      -H "X-Tenant-Id: $TENANT_ID"
  fi
}

assert_code_zero() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
payload = sys.argv[2]

try:
    data = json.loads(payload)
except Exception as exc:
    print(f"[FAIL] {name}: 返回不是 JSON: {exc}")
    print(payload)
    sys.exit(1)

if data.get("code") != 0:
    print(f"[FAIL] {name}: code != 0")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)

print(f"[OK] {name}")
PY
}

assert_outline_usable() {
  local payload="$1"

  python3 - "$payload" <<'PY'
import json
import sys

data = json.loads(sys.argv[1])
body = data.get("data") or {}
if not body.get("usable"):
    print("[FAIL] outline-status: usable=false")
    print(json.dumps(body, ensure_ascii=False, indent=2))
    sys.exit(1)
print("[OK] outline-status: usable=true")
PY
}

assert_sync_not_failed() {
  local payload="$1"

  python3 - "$payload" <<'PY'
import json
import sys

root = json.loads(sys.argv[1])
data = root.get("data") or {}
status = str(data.get("status") or data.get("taskStatus") or "").upper()
failed = int(data.get("failed") or data.get("failedCount") or 0)
if status == "FAILED" or failed > 0:
    print("[FAIL] outline-sync: 同步任务失败或存在失败文档")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
print("[OK] outline-sync-result")
PY
}

assert_outline_stats_ready() {
  local name="$1"
  local payload="$2"

  python3 - "$name" "$payload" <<'PY'
import json
import sys

name = sys.argv[1]
root = json.loads(sys.argv[2])
data = root.get("data") or {}
chunk_count = int(data.get("chunkCount") or data.get("chunk_count") or 0)
doc_count = int(data.get("documentCount") or data.get("document_count") or 0)
if doc_count <= 0 or chunk_count <= 0:
    print(f"[FAIL] {name}: documentCount/chunkCount 不足")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
print(f"[OK] {name}: documents={doc_count} chunks={chunk_count}")
PY
}

assert_search_hits_outline() {
  local payload="$1"

  python3 - "$payload" <<'PY'
import json
import sys

root = json.loads(sys.argv[1])
data = root.get("data") or {}
hits = data.get("hits") or []
keywords = ["PCI", "裂缝", "坑槽", "灌缝", "铣刨", "现场复核", "来源上下文"]
if not hits:
    print("[FAIL] ai-knowledge-search: 未命中 OUTLINE 知识")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
outline_hits = [h for h in hits if str(h.get("sourceType") or h.get("source_type") or "").upper() == "OUTLINE"]
text = "\n".join(str(h.get("title") or "") + "\n" + str(h.get("content") or "") for h in hits)
if not outline_hits:
    print("[FAIL] ai-knowledge-search: 命中结果没有 OUTLINE sourceType")
    print(json.dumps(hits[:3], ensure_ascii=False, indent=2))
    sys.exit(1)
if not any(k in text for k in keywords):
    print("[FAIL] ai-knowledge-search: 命中内容缺少养护关键词")
    print(json.dumps(hits[:3], ensure_ascii=False, indent=2))
    sys.exit(1)
print(f"[OK] ai-knowledge-search: outlineHits={len(outline_hits)}")
PY
}

assert_tool_retrieve_hits() {
  local payload="$1"

  python3 - "$payload" <<'PY'
import json
import sys

root = json.loads(sys.argv[1])
tool = root.get("data") or {}
if not tool.get("success"):
    print("[FAIL] knowledge.retrieve: 工具执行失败")
    print(json.dumps(tool, ensure_ascii=False, indent=2))
    sys.exit(1)
count = int(tool.get("count") or 0)
data = tool.get("data") or {}
hits = data.get("hits") or []
request = data.get("request") or {}
if count <= 0 or not hits:
    print("[FAIL] knowledge.retrieve: 未命中知识")
    print(json.dumps(tool, ensure_ascii=False, indent=2))
    sys.exit(1)
if request.get("topK") is None and request.get("top_k") is None:
    print("[FAIL] knowledge.retrieve: 缺少 request summary")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
outline_hits = [h for h in hits if str(h.get("sourceType") or h.get("source_type") or "").upper() == "OUTLINE"]
if not outline_hits:
    print("[FAIL] knowledge.retrieve: 命中结果没有 OUTLINE 来源")
    print(json.dumps(hits[:3], ensure_ascii=False, indent=2))
    sys.exit(1)
print(f"[OK] knowledge.retrieve: outlineHits={len(outline_hits)}")
PY
}

assert_map_agent_response() {
  local payload="$1"
  local required="$2"

  python3 - "$payload" "$required" <<'PY'
import json
import sys

root = json.loads(sys.argv[1])
required = sys.argv[2].lower() == "true"
data = root.get("data") or {}
answer = str(data.get("answer") or "")
intent = str(data.get("intent") or "")
tool_results = data.get("toolResults") or data.get("tool_results") or []
sources = data.get("sources") or data.get("knowledgeSources") or data.get("knowledge_sources") or []

if "LangGraph Runtime 不可用" in answer or intent == "ERROR":
    print("[WARN] map-agent-run: LangGraph Runtime 不可用，已完成 Outline 知识检索和工具验证")
    print(answer)
    if required:
        sys.exit(1)
    sys.exit(0)

knowledge_tools = [
    item for item in tool_results
    if (item.get("toolName") or item.get("name")) == "knowledge.retrieve"
]
outline_sources = [
    item for item in sources
    if str(item.get("sourceType") or item.get("source_type") or "").upper() == "OUTLINE"
]
if not knowledge_tools and not outline_sources:
    print("[FAIL] map-agent-run: 响应中没有 knowledge.retrieve 或 OUTLINE sources")
    print(json.dumps(data, ensure_ascii=False, indent=2)[:4000])
    sys.exit(1)
print(f"[OK] map-agent-run: knowledgeTools={len(knowledge_tools)} outlineSources={len(outline_sources)}")
PY
}

require_cmd curl
require_cmd python3

echo "==> Outline → AI 助手链路验收：$BASE_URL tenant=$TENANT_ID"

status="$(request GET "$BASE_URL/api/outline/status")"
assert_code_zero "outline-status" "$status"
assert_outline_usable "$status"

sync_payload="{\"limit\":$OUTLINE_SYNC_LIMIT,\"force\":$OUTLINE_SYNC_FORCE}"
sync_result="$(request POST "$BASE_URL/api/outline/sync" "$sync_payload")"
assert_code_zero "outline-sync" "$sync_result"
assert_sync_not_failed "$sync_result"

outline_stats="$(request GET "$BASE_URL/api/outline/knowledge-stats")"
assert_code_zero "outline-knowledge-stats" "$outline_stats"
assert_outline_stats_ready "outline-knowledge-stats" "$outline_stats"

knowledge_stats="$(request GET "$BASE_URL/api/ai/knowledge/stats?tenantId=$TENANT_ID")"
assert_code_zero "ai-knowledge-stats" "$knowledge_stats"
assert_outline_stats_ready "ai-knowledge-stats" "$knowledge_stats"

search_payload="$(python3 - "$TENANT_ID" "$TOP_K" "$AI_QUESTION" <<'PY'
import json
import sys

tenant_id, top_k, question = sys.argv[1], int(sys.argv[2]), sys.argv[3]
print(json.dumps({
    "tenantId": tenant_id,
    "query": question,
    "topK": top_k,
    "sourceTypes": ["OUTLINE"],
}, ensure_ascii=False))
PY
)"
search_result="$(request POST "$BASE_URL/api/ai/knowledge/search" "$search_payload")"
assert_code_zero "ai-knowledge-search" "$search_result"
assert_search_hits_outline "$search_result"

tool_payload="$(python3 - "$TENANT_ID" "$TOP_K" "$AI_QUESTION" <<'PY'
import json
import sys

tenant_id, top_k, question = sys.argv[1], int(sys.argv[2]), sys.argv[3]
print(json.dumps({
    "toolName": "knowledge.retrieve",
    "tenantId": tenant_id,
    "userQuestion": question,
    "mapContext": {
        "tenantId": tenant_id,
        "userQuestion": question,
        "routeCode": "Y027140727",
        "year": 2026,
        "mapObject": {
            "objectType": "ASSESSMENT_RESULT",
            "routeCode": "Y027140727",
            "indexCode": "PCI",
            "grade": "中",
            "pci": 68.5,
            "diseaseType": "裂缝,坑槽"
        }
    },
    "options": {
        "topK": top_k,
        "useKnowledge": True,
        "useOutline": True
    },
    "args": {
        "query": question,
        "filters": {
            "sourceType": "OUTLINE"
        }
    }
}, ensure_ascii=False))
PY
)"
tool_result="$(request POST "$BASE_URL/api/agent/tools/execute" "$tool_payload")"
assert_code_zero "knowledge.retrieve" "$tool_result"
assert_tool_retrieve_hits "$tool_result"

agent_payload="$(python3 - "$TENANT_ID" "$TOP_K" "$AI_QUESTION" <<'PY'
import json
import sys

tenant_id, top_k, question = sys.argv[1], int(sys.argv[2]), sys.argv[3]
print(json.dumps({
    "message": question,
    "action": "CHAT",
    "mapContext": {
        "tenantId": tenant_id,
        "userQuestion": question,
        "routeCode": "Y027140727",
        "year": 2026,
        "mapObject": {
            "objectType": "ASSESSMENT_RESULT",
            "routeCode": "Y027140727",
            "indexCode": "PCI",
            "grade": "中",
            "pci": 68.5,
            "diseaseType": "裂缝,坑槽"
        }
    },
    "options": {
        "topK": top_k,
        "useBusinessData": True,
        "useKnowledge": True,
        "useOutline": True,
        "requireAi": True,
        "traceId": "outline-ai-assistant-smoke"
    }
}, ensure_ascii=False))
PY
)"
agent_result="$(request POST "$BASE_URL/api/agent/map-agent/run" "$agent_payload")"
assert_code_zero "map-agent-run" "$agent_result"
assert_map_agent_response "$agent_result" "$MAP_AGENT_REQUIRED"

echo "==> Outline → AI 助手链路验收完成"
