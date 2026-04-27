#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"

echo "==> /api/demo/status"
curl -s "$BASE_URL/api/demo/status?tenantId=$TENANT_ID&year=$YEAR" -H "X-Tenant-Id: $TENANT_ID" | python3 -m json.tool

echo ""
echo "==> /api/demo/dashboard"
curl -s "$BASE_URL/api/demo/dashboard?tenantId=$TENANT_ID&year=$YEAR" -H "X-Tenant-Id: $TENANT_ID" | python3 -m json.tool