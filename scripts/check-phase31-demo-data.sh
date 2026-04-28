#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"

echo "==> GET /api/demo/status"
curl -fsS "$BASE_URL/api/demo/status?tenantId=$TENANT_ID&year=$YEAR" \
  -H "X-Tenant-Id: $TENANT_ID" | python3 -m json.tool

echo
echo "==> GET /api/demo/dashboard"
DASHBOARD="$(curl -fsS "$BASE_URL/api/demo/dashboard?tenantId=$TENANT_ID&year=$YEAR" \
  -H "X-Tenant-Id: $TENANT_ID")"
echo "$DASHBOARD" | python3 -m json.tool | head -200

python3 - "$DASHBOARD" <<'PY'
import json, sys
payload=json.loads(sys.argv[1])
data=payload.get("data") or payload
summary=data.get("summary") or {}
checks={
    "route_count": summary.get("route_count", summary.get("routeCount", 0)),
    "assessment_count": summary.get("assessment_count", summary.get("assessmentCount", 0)),
    "disease_count": summary.get("disease_count", summary.get("diseaseCount", 0)),
}
warn=[]
for k,v in checks.items():
    try:
        if float(v or 0) <= 0:
            warn.append(k)
    except Exception:
        warn.append(k)
if warn:
    print("[WARN] dashboard summary 中这些计数为空或为 0:", ", ".join(warn))
else:
    print("[OK] demo dashboard summary 有数据")
PY
