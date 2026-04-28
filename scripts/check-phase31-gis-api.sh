#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"
ROUTE_CODE="${ROUTE_CODE:-G210}"

json_get() {
  local name="$1"
  local url="$2"
  echo
  echo "==> $name"
  curl -fsS "$url" -H "X-Tenant-Id: $TENANT_ID" | python3 -m json.tool | head -120
}

json_get "GET /api/gis/layers" "$BASE_URL/api/gis/layers"
json_get "GET /api/gis/road-routes" "$BASE_URL/api/gis/road-routes?routeCode=$ROUTE_CODE"
json_get "GET /api/gis/diseases" "$BASE_URL/api/gis/diseases?routeCode=$ROUTE_CODE&pageSize=5"
json_get "GET /api/gis/assessment-results" "$BASE_URL/api/gis/assessment-results?routeCode=$ROUTE_CODE&year=$YEAR&pageSize=5"

echo
echo "==> POST /api/gis/map-statistics"
curl -fsS -X POST "$BASE_URL/api/gis/map-statistics" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d "{\"routeCode\":\"$ROUTE_CODE\",\"year\":$YEAR}" | python3 -m json.tool | head -160

echo
echo "[OK] GIS API 检查完成"
