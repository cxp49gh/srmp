#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

echo "==> Health"
curl -s "$BASE_URL/api/health" | jq . || curl -s "$BASE_URL/api/health"

echo "==> GIS route layer"
curl -s -H "X-Tenant-Id: $TENANT_ID" "$BASE_URL/api/gis/road-routes?routeCode=G210" | jq . || true

echo "==> GIS disease layer"
curl -s -H "X-Tenant-Id: $TENANT_ID" "$BASE_URL/api/gis/diseases?routeCode=G210" | jq . || true

echo "==> GIS assessment layer"
curl -s -H "X-Tenant-Id: $TENANT_ID" "$BASE_URL/api/gis/assessment-results?routeCode=G210&year=2026&indexCode=MQI" | jq . || true

echo "==> AI route analysis"
curl -s -X POST "$BASE_URL/api/agent/analyze/route" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{"routeCode":"G210","year":2026}' | jq . || true

echo "==> Smoke test finished"
