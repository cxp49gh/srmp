#!/usr/bin/env bash
# 数据库初始化：单一 SQL 入口 srmp-admin/src/main/resources/db/srmp_full_init.sql
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

FULL_SQL="${FULL_SQL:-$ROOT_DIR/srmp-admin/src/main/resources/db/srmp_full_init.sql}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-srmp}"
DB_USER="${DB_USER:-srmp}"
DB_PASSWORD="${DB_PASSWORD:-srmp123}"
PSQL_BIN="${PSQL_BIN:-}"
RESET_DEMO=0
LOCAL_DEV=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --reset-demo)
      RESET_DEMO=1
      shift
      ;;
    --local-dev)
      LOCAL_DEV=1
      shift
      ;;
    --help)
      echo "Usage: $0 [--reset-demo] [--local-dev]"
      echo "执行单一文件: srmp-admin/src/main/resources/db/srmp_full_init.sql"
      echo "可用环境变量 FULL_SQL 覆盖该文件路径。"
      echo "全量 SQL 仅 srmp_full_init.sql；说明脚本: powershell -File scripts/rebuild-srmp-full-init.ps1"
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

docker_psql_available() {
  command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx 'srmp-postgres'
}

ensure_psql_available() {
  if [ -n "$PSQL_BIN" ] || docker_psql_available || command -v psql >/dev/null 2>&1; then
    return 0
  fi
  echo "[FAIL] psql is unavailable. Start docker compose postgres first, install PostgreSQL client, or set PSQL_BIN."
  exit 1
}

run_sql_file() {
  local file="$1"
  local label="$2"
  test -f "$file" || {
    echo "[FAIL] SQL file missing: $file"
    exit 1
  }

  echo "==> $label"
  if [ -z "$PSQL_BIN" ] && [ "$LOCAL_DEV" = "0" ] && docker_psql_available; then
    docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$file"
  elif [ -z "$PSQL_BIN" ] && docker_psql_available; then
    docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$file"
  else
    local bin="${PSQL_BIN:-psql}"
    PGPASSWORD="$DB_PASSWORD" "$bin" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$file"
  fi
  echo "[OK] $label"
}

run_sql_text() {
  local label="$1"
  local sql="$2"
  echo "==> $label"
  if [ -z "$PSQL_BIN" ] && docker_psql_available; then
    printf '%s\n' "$sql" | docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
  else
    local bin="${PSQL_BIN:-psql}"
    printf '%s\n' "$sql" | PGPASSWORD="$DB_PASSWORD" "$bin" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
  fi
  echo "[OK] $label"
}

ensure_psql_available

if [ "$RESET_DEMO" = "1" ]; then
  run_sql_text "reset G210 demo data" "
DELETE FROM index_result WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM assessment_result WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM disease_record WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_evaluation_unit WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_section WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_route WHERE tenant_id = 'default' AND route_code = 'G210';
"
fi

run_sql_file "$FULL_SQL" "srmp full init (single SQL)"

echo "[OK] SRMP database and demo data initialized"
