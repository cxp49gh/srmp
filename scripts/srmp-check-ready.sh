#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BACKEND_URL="${BACKEND_URL:-http://localhost:${BACKEND_PORT:-8080}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT:-5173}}"
ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:${SRMP_AI_ORCHESTRATOR_PORT:-18080}}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"
ROUTE_CODE="${ROUTE_CODE:-G210}"
BACKEND_ONLY=0
FRONTEND_ONLY=0
NO_ORCHESTRATOR=0

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
    --no-orchestrator)
      NO_ORCHESTRATOR=1
      shift
      ;;
    --help)
      echo "Usage: $0 [--backend-only] [--frontend-only] [--no-orchestrator]"
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

retry_json() {
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
      if curl -fsS "$url" >/tmp/srmp-ready-response.json 2>/tmp/srmp-ready-error.log; then
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

psql_scalar() {
  local sql="$1"
  docker exec -i srmp-postgres env PGPASSWORD="${DB_PASSWORD:-srmp123}" psql -U "${DB_USER:-srmp}" -d "${DB_NAME:-srmp}" -At -v ON_ERROR_STOP=1 <<< "$sql"
}

assert_db_min() {
  local label="$1"
  local sql="$2"
  local minimum="$3"
  local value
  value="$(psql_scalar "$sql" | tr -d '[:space:]')"
  if [ -z "$value" ] || [ "$value" -lt "$minimum" ]; then
    echo "[FAIL] $label below threshold: ${value:-empty} < $minimum"
    exit 1
  fi
  echo "[OK] $label: $value"
}

assert_table_exists() {
  local table="$1"
  local exists
  exists="$(psql_scalar "select to_regclass('public.$table') is not null;" | tr -d '[:space:]')"
  if [ "$exists" != "t" ]; then
    echo "[FAIL] table missing: $table"
    exit 1
  fi
  echo "[OK] table exists: $table"
}

require_cmd curl
require_cmd python3

if [ "$FRONTEND_ONLY" = "0" ]; then
  require_cmd docker
  require_container srmp-postgres
  require_container srmp-redis
  require_container srmp-minio
  if [ "$NO_ORCHESTRATOR" = "0" ]; then
    require_container srmp-ai-orchestrator
  fi
fi

if [ "$FRONTEND_ONLY" = "0" ]; then
  assert_table_exists "ai_solution_template"
  assert_table_exists "ai_solution_template_version"
  assert_table_exists "ai_trace_log"
  assert_table_exists "ai_trace_step"
  assert_db_min "enabled solution templates" "select count(*) from ai_solution_template where tenant_id='${TENANT_ID}' and status='ENABLED' and deleted=false;" 5
  assert_db_min "solution template versions" "select count(*) from ai_solution_template_version where tenant_id='${TENANT_ID}';" 5

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
# 全量初始化已不含 G210 演示种子：仅校验接口可用且表计数非负
required = {
    'road_route': 0,
    'road_section_line': 0,
    'road_section_ledger': 0,
    'road_section_km': 0,
    'road_section_hm': 0,
    'assessment_result': 0,
    'disease_record': 0,
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

if [ "$FRONTEND_ONLY" = "0" ] && [ "$NO_ORCHESTRATOR" = "0" ]; then
  retry_json "LangGraph health" "$ORCHESTRATOR_URL/health"
  python3 - <<'PY'
import json
import sys

with open('/tmp/srmp-ready-response.json', 'r', encoding='utf-8') as fh:
    payload = json.load(fh)

if payload.get('status') != 'UP':
    print('[FAIL] LangGraph /health status != UP')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
print('[OK] LangGraph health status UP')
PY

  retry_json "LangGraph ready" "$ORCHESTRATOR_URL/ready" "X-Tenant-Id" "$TENANT_ID"
  python3 - <<'PY'
import json
import sys

with open('/tmp/srmp-ready-response.json', 'r', encoding='utf-8') as fh:
    payload = json.load(fh)

if payload.get('status') != 'UP':
    print('[FAIL] LangGraph /ready status != UP')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
gateway = payload.get('toolGateway') or {}
if gateway.get('ok') is not True:
    print('[FAIL] LangGraph Tool Gateway is not ready')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
print('[OK] LangGraph ready and Tool Gateway reachable')
PY

  retry_json "Java orchestrator ops summary" "$BACKEND_URL/api/agent/orchestrator/ops/summary?recentLimit=5" "X-Tenant-Id" "$TENANT_ID"
  python3 - <<'PY'
import json
import sys

with open('/tmp/srmp-ready-response.json', 'r', encoding='utf-8') as fh:
    payload = json.load(fh)

if payload.get('code') != 0:
    print('[FAIL] Java ops summary code != 0')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
data = payload.get('data') or {}
if data.get('provider') != 'langgraph':
    print('[FAIL] Java orchestrator provider != langgraph')
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
ready = data.get('langgraphReady') or {}
body = ready.get('body') or {}
if ready.get('ok') is not True or body.get('status') != 'UP':
    print('[FAIL] Java ops summary reports LangGraph not ready')
    print(json.dumps(data, ensure_ascii=False, indent=2))
    sys.exit(1)
print('[OK] Java ops summary reports langgraph provider and ready runtime')
PY

  retry_json "Java orchestrator runtime config" "$BACKEND_URL/api/agent/orchestrator/ops/config" "X-Tenant-Id" "$TENANT_ID"
  python3 - <<'PY'
import json
import sys

with open('/tmp/srmp-ready-response.json', 'r', encoding='utf-8') as fh:
    payload = json.load(fh)

data = payload.get('data') or {}
body = data.get('body') or {}
safe_config = body.get('safeConfig') or {}
if payload.get('code') != 0 or data.get('ok') is not True or not safe_config.get('strategyVersion'):
    print('[FAIL] Java ops config proxy did not return runtime safeConfig')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
print('[OK] Java ops config proxy returned runtime safeConfig')
PY

  retry_json "Java orchestrator health detail" "$BACKEND_URL/api/agent/orchestrator/ops/health-detail?includeGateway=true&includeContract=true" "X-Tenant-Id" "$TENANT_ID"
  python3 - <<'PY'
import json
import sys

with open('/tmp/srmp-ready-response.json', 'r', encoding='utf-8') as fh:
    payload = json.load(fh)

data = payload.get('data') or {}
body = data.get('body') or {}
if payload.get('code') != 0 or data.get('ok') is not True or body.get('status') == 'DOWN':
    print('[FAIL] Java ops health detail is DOWN')
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)
print('[OK] Java ops health detail is not DOWN')
PY
fi

if [ "$BACKEND_ONLY" = "0" ]; then
  retry_url "frontend page" "$FRONTEND_URL/"
fi

echo "[OK] SRMP is ready"
