#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

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

run_sql_file "srmp-admin/src/main/resources/db/schema.sql" "base schema"

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

run_sql_file "srmp-admin/src/main/resources/db/init_dict.sql" "dictionary data"
run_sql_file "srmp-admin/src/main/resources/db/init_admin.sql" "admin data"
run_sql_file "srmp-admin/src/main/resources/db/phase17_outline_sync.sql" "outline sync schema"
run_sql_file "srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql" "AI vector knowledge schema"
run_sql_file "srmp-admin/src/main/resources/db/phase37_1_knowledge_reindex.sql" "AI knowledge reindex metadata"
<<<<<<< ours
=======
run_sql_file "srmp-admin/src/main/resources/db/phase40_outline_ai_knowledge_sync_fix.sql" "Outline AI knowledge sync fix"
run_sql_file "srmp-admin/src/main/resources/db/phase41_outline_vectorize_ops.sql" "Outline vectorize ops indexes"
>>>>>>> theirs
run_sql_file "srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql" "AI solution template schema"
run_sql_file "srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql" "AI solution generation schema"
run_sql_file "srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql" "AI trace schema"
run_sql_file "srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql" "AI solution draft version schema"
run_sql_file "srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql" "template effectiveness schema"
run_sql_file "srmp-admin/src/main/resources/db/phase35_sample_solution_templates.sql" "sample solution templates"
run_sql_file "srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql" "G210 2026 demo business data"

echo "[OK] SRMP database and demo data initialized"
