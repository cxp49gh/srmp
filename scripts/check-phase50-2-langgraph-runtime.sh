#!/usr/bin/env bash
set -euo pipefail

ORCH_URL="${SRMP_LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"

printf '\n[1/3] check orchestrator health: %s/health\n' "$ORCH_URL"
curl -fsS "$ORCH_URL/health" | sed 's/,/,&/g'

printf '\n\n[2/3] check map-agent run through LangGraph runtime\n'
RESP_FILE="$(mktemp)"
curl -fsS -X POST "$ORCH_URL/api/srmp/langgraph/map-agent/run" \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "message":"G210 中度裂缝怎么处置？",
    "mapContext":{
      "tenantId":"'"${TENANT_ID}"'",
      "mode":"OBJECT",
      "routeCode":"G210",
      "year":2024,
      "mapObject":{"objectType":"DISEASE_RECORD","diseaseName":"横向裂缝","severity":"中度"}
    },
    "options":{"topK":8,"useKnowledge":true}
  }' | tee "$RESP_FILE"

printf '\n\n[3/3] assert response fields\n'
python3 - "$RESP_FILE" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as f:
    data = json.load(f)

assert data.get('mode') == 'LANGGRAPH_MAP_AGENT', data
assert data.get('answer'), data
assert data.get('trace', {}).get('steps'), data
print('PASS: LangGraph runtime response contract is valid')
PY
