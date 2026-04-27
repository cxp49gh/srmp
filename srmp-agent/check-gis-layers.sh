#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
ROUTE_CODE="${ROUTE_CODE:-G210}"
YEAR="${YEAR:-2026}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] 缺少命令：$1"
    exit 1
  }
}

require_cmd curl
require_cmd python3

check_layer() {
  local name="$1"
  local url="$2"
  echo "==> $name"
  RESP="$(curl -s "$url" -H "X-Tenant-Id: $TENANT_ID")"
  python3 - "$RESP" "$name" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
name=sys.argv[2]
if payload.get("code") != 0:
    print(f"[FAIL] {name}: code={payload.get('code')} message={payload.get('message')}")
    sys.exit(1)
data=payload.get("data") or {}
features=data.get("features") or []
print(f"[OK] {name}: features={len(features)}")
if len(features)==0:
    sys.exit(2)
PY
}

check_layer "road-routes" "$BASE_URL/api/gis/road-routes?routeCode=$ROUTE_CODE"
check_layer "road-sections" "$BASE_URL/api/gis/road-sections?routeCode=$ROUTE_CODE"
check_layer "evaluation-units" "$BASE_URL/api/gis/evaluation-units?routeCode=$ROUTE_CODE"
check_layer "diseases" "$BASE_URL/api/gis/diseases?routeCode=$ROUTE_CODE"
check_layer "assessment-results" "$BASE_URL/api/gis/assessment-results?routeCode=$ROUTE_CODE&year=$YEAR"

echo "==> map-statistics"
curl -s -X POST "$BASE_URL/api/gis/map-statistics" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d "{\"routeCode\":\"$ROUTE_CODE\",\"year\":$YEAR}" | python3 -m json.tool

echo "[OK] GIS 图层接口联调完成"
