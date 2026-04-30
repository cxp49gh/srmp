#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BACKEND_URL="${BACKEND_URL:-http://localhost:${BACKEND_PORT:-8080}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT:-5173}}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"
ROUTE_CODE="${ROUTE_CODE:-G210}"
BACKEND_ONLY=0
FRONTEND_ONLY=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --backend-only)
      BACKEND_ONLY=1
      shift
      ;;
    --frontend-only)
      FRONTEND_ONLY=1
      shift
      ;;
    --help)
      echo "Usage: $0 [--backend-only] [--frontend-only]"
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] missing command: $1"
    exit 1
  }
}

require_container() {
  local name="$1"
  if docker ps --format '{{.Names}}' | grep -qx "$name"; then
    echo "[OK] $name running"
  else
    echo "[FAIL] $name is not running"
    exit 1
  fi
}

retry_url() {
  local label="$1"
  local url="$2"
  local header_name="${3:-}"
  local header_value="${4:-}"
  local i
  for i in $(seq 1 60); do
    if [ -n "$header_name" ]; then
      if curl -fsS "$url" -H "$header_name: $header_value" >/tmp/srmp-ready-response.json 2>/tmp/srmp-ready-error.log; then
        echo "[OK] $label"
        return 0
      fi
    else
      if curl -fsS "$url" >/tmp/srmp-ready-response.txt 2>/tmp/srmp-ready-error.log; then
        echo "[OK] $label"
        return 0
      fi
    fi
    sleep 2
  done
  echo "[FAIL] $label timeout: $url"
  cat /tmp/srmp-ready-error.log || true
  exit 1
}

require_cmd curl
require_cmd python3

if [ "$FRONTEND_ONLY" = "0" ]; then
  require_cmd docker
  require_container srmp-postgres
  require_container srmp-redis
  require_container srmp-minio
fi

if [ "$FRONTEND_ONLY" = "0" ]; then
  retry_url "backend GIS route API" "$BACKEND_URL/api/gis/road-routes?routeCode=$ROUTE_CODE" "X-Tenant-Id" "$TENANT_ID"
  retry_url "demo status API" "$BACKEND_URL/api/demo/status?tenantId=$TENANT_ID&year=$YEAR" "X-Tenant-Id" "$TENANT_ID"
  python3 - <<'PY'
import json
import sys

with open('/tmp/srmp-ready-response.json', 'r', encoding='utf-8') as fh:
    payload = json.load(fh)

if payload.get('code') != 0:
    print('[FAIL] demo status API code != 0')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)

data = payload.get('data') or {}
tables = data.get('tables') or {}
required = {
    'road_route': 1,
    'road_section': 4,
    'road_evaluation_unit': 1000,
    'assessment_result': 1000,
    'disease_record': 3000,
}
missing = []
for name, minimum in required.items():
    if int(tables.get(name) or 0) < minimum:
        missing.append(f"{name}={tables.get(name)} < {minimum}")
if missing:
    print('[FAIL] demo data below threshold: ' + '; '.join(missing))
    sys.exit(1)
print('[OK] demo data thresholds satisfied')
PY
fi

if [ "$BACKEND_ONLY" = "0" ]; then
  retry_url "frontend page" "$FRONTEND_URL/"
fi

echo "[OK] SRMP is ready"
