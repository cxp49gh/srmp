#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
ROUTE_CODE="${ROUTE_CODE:-G210}"
YEAR="${YEAR:-2026}"

require_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "[FAIL] 缺少命令：$1"; exit 1; }; }
require_cmd curl
require_cmd python3

echo "==> 获取一个评定结果对象"
FEATURE_RESP="$(curl -s "$BASE_URL/api/gis/assessment-results?routeCode=$ROUTE_CODE&year=$YEAR" -H "X-Tenant-Id: $TENANT_ID")"
MAP_OBJECT="$(python3 - "$FEATURE_RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
features=(payload.get('data') or {}).get('features') or []
if not features:
    print('')
    sys.exit(0)
p=features[0].get('properties') or {}
print(json.dumps({
    'id': p.get('id') or p.get('objectId'),
    'objectId': p.get('id') or p.get('objectId'),
    'objectType': p.get('objectType') or 'ASSESSMENT_RESULT',
    'routeCode': p.get('routeCode'),
    'year': p.get('year'),
    'startStake': p.get('startStake'),
    'endStake': p.get('endStake'),
    'mqi': p.get('mqi'),
    'pqi': p.get('pqi'),
    'pci': p.get('pci'),
    'grade': p.get('grade')
}, ensure_ascii=False))
PY
)"

if [ -z "$MAP_OBJECT" ]; then
  echo "[FAIL] 未获取到评定结果对象，请先检查 GIS 图层接口"
  exit 1
fi

echo "mapObject=$MAP_OBJECT"

echo "==> 发起带地图对象上下文的 AI 问答"
REQ="$(python3 - "$MAP_OBJECT" <<'PY'
import json, sys
obj=json.loads(sys.argv[1])
print(json.dumps({
  'message':'这个评定单元为什么分低？请给出处置建议。',
  'context':{'routeCode':obj.get('routeCode'),'year':obj.get('year') or 2026,'mapObject':obj},
  'options':{'useBusinessData':True,'useKnowledge':False,'useOutline':False,'topK':5}
}, ensure_ascii=False))
PY
)"

RESP="$(curl -s -X POST "$BASE_URL/api/agent/chat" -H "Content-Type: application/json" -H "X-Tenant-Id: $TENANT_ID" -d "$REQ")"
python3 - "$RESP" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
if payload.get('code') != 0:
    print('[FAIL] code != 0')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
data=payload.get('data') or {}
if not data.get('mapObjectUsed'):
    print('[FAIL] data.mapObjectUsed 不是 true')
    print(json.dumps(data, ensure_ascii=False, indent=2)[:2000])
    sys.exit(1)
answer=data.get('answer') or payload.get('answer') or ''
print('[OK] mapObjectUsed=true')
print(str(answer)[:800])
PY
