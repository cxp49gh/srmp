#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

LOAD_ENV=1

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-env)
      LOAD_ENV=0
      shift
      ;;
    --help)
      cat <<'USAGE'
Usage: ./scripts/srmp-check-dev-schema.sh [--no-env]

Checks the external development PostgreSQL schema before starting SRMP app
containers. The check is read-only and does not start local postgres/redis/minio.

Environment:
  .env.dev is loaded by default.
  DEV_DB_HOST / DEV_DB_PORT / DEV_DB_NAME / DEV_DB_USER / DEV_DB_PASSWORD
  PSQL_BIN                         Optional local psql binary.
  DEV_SCHEMA_CHECK_PSQL_IMAGE      Optional existing Docker image with psql.
  DEV_SCHEMA_CHECK_PULL_IMAGE=1    Allow pulling postgres:15-alpine if needed.
USAGE
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

if [ "$LOAD_ENV" = "1" ] && [ -f "$ROOT_DIR/.env.dev" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.dev"
  set +a
fi

DEV_DB_HOST="${DEV_DB_HOST:-${DB_HOST:-127.0.0.1}}"
DEV_DB_PORT="${DEV_DB_PORT:-${DB_PORT:-5432}}"
DEV_DB_NAME="${DEV_DB_NAME:-${DB_NAME:-srmp}}"
DEV_DB_USER="${DEV_DB_USER:-${DB_USER:-srmp}}"
DEV_DB_PASSWORD="${DEV_DB_PASSWORD:-${DB_PASSWORD:-srmp123}}"

REPAIR_SQL="srmp-road-asset/src/main/resources/db/data_mgmt_enhance.sql"

required_sql() {
  cat <<'SQL'
WITH expected(name, ok) AS (
  VALUES
    (
      'data_mgmt_project',
      to_regclass('public.data_mgmt_project') IS NOT NULL
    ),
    (
      'data_mgmt_project.archived',
      EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'data_mgmt_project'
          AND column_name = 'archived'
      )
    ),
    (
      'data_mgmt_project.archived_at',
      EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'data_mgmt_project'
          AND column_name = 'archived_at'
      )
    ),
    (
      'data_mgmt_project.archived_by',
      EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'data_mgmt_project'
          AND column_name = 'archived_by'
      )
    ),
    (
      'data_mgmt_audit_log',
      to_regclass('public.data_mgmt_audit_log') IS NOT NULL
    )
)
SELECT name
FROM expected
WHERE NOT ok
ORDER BY name;
SQL
}

pick_local_psql() {
  if [ -n "${PSQL_BIN:-}" ]; then
    printf '%s\n' "$PSQL_BIN"
    return 0
  fi
  if command -v psql >/dev/null 2>&1; then
    command -v psql
    return 0
  fi
  return 1
}

pick_docker_psql_image() {
  local image
  if [ -n "${DEV_SCHEMA_CHECK_PSQL_IMAGE:-}" ]; then
    if docker image inspect "$DEV_SCHEMA_CHECK_PSQL_IMAGE" >/dev/null 2>&1; then
      printf '%s\n' "$DEV_SCHEMA_CHECK_PSQL_IMAGE"
      return 0
    fi
    echo "[FAIL] DEV_SCHEMA_CHECK_PSQL_IMAGE not found locally: $DEV_SCHEMA_CHECK_PSQL_IMAGE" >&2
    return 1
  fi

  for image in \
    postgres:15 \
    postgres:15-alpine \
    postgis/postgis:15-3.4 \
    postgis/postgis:16-3.4 \
    postgis-postgis-15-3.4-pgvector:latest \
    bitnami/postgresql:15.2.0; do
    if docker image inspect "$image" >/dev/null 2>&1; then
      printf '%s\n' "$image"
      return 0
    fi
  done

  if [ "${DEV_SCHEMA_CHECK_PULL_IMAGE:-0}" = "1" ]; then
    printf '%s\n' "postgres:15-alpine"
    return 0
  fi

  echo "[FAIL] psql is unavailable. Install PostgreSQL client, set PSQL_BIN, or set DEV_SCHEMA_CHECK_PSQL_IMAGE to an existing Docker image with psql." >&2
  return 1
}

run_query_with_local_psql() {
  local bin="$1"
  PGPASSWORD="$DEV_DB_PASSWORD" "$bin" \
    -h "$DEV_DB_HOST" \
    -p "$DEV_DB_PORT" \
    -U "$DEV_DB_USER" \
    -d "$DEV_DB_NAME" \
    -At \
    -v ON_ERROR_STOP=1
}

run_query_with_docker_psql() {
  local image="$1"
  local docker_host="$DEV_DB_HOST"
  local network_args=()

  if [ "$DEV_DB_HOST" = "127.0.0.1" ] || [ "$DEV_DB_HOST" = "localhost" ] || [ "$DEV_DB_HOST" = "::1" ]; then
    if [ "$(uname -s)" = "Linux" ]; then
      network_args=(--network host)
    else
      docker_host="host.docker.internal"
    fi
  fi

  if [ "${#network_args[@]}" -gt 0 ]; then
    docker run --rm \
      "${network_args[@]}" \
      -e PGPASSWORD="$DEV_DB_PASSWORD" \
      "$image" \
      psql \
        -h "$docker_host" \
        -p "$DEV_DB_PORT" \
        -U "$DEV_DB_USER" \
        -d "$DEV_DB_NAME" \
        -At \
        -v ON_ERROR_STOP=1
  else
    docker run --rm \
      -e PGPASSWORD="$DEV_DB_PASSWORD" \
      "$image" \
      psql \
        -h "$docker_host" \
        -p "$DEV_DB_PORT" \
        -U "$DEV_DB_USER" \
        -d "$DEV_DB_NAME" \
        -At \
        -v ON_ERROR_STOP=1
  fi
}

echo "==> 检查开发库 schema: $DEV_DB_HOST:$DEV_DB_PORT/$DEV_DB_NAME"

missing=""
if local_psql="$(pick_local_psql)"; then
  missing="$(required_sql | run_query_with_local_psql "$local_psql")"
else
  command -v docker >/dev/null 2>&1 || {
    echo "[FAIL] missing command: docker"
    exit 1
  }
  docker_image="$(pick_docker_psql_image)"
  missing="$(required_sql | run_query_with_docker_psql "$docker_image")"
fi

if [ -n "$missing" ]; then
  echo "[FAIL] 开发库缺少必要 schema 对象："
  printf '%s\n' "$missing" | sed 's/^/  - /'
  echo
  echo "修复方式："
  echo "  PGPASSWORD='***' psql -h '$DEV_DB_HOST' -p '$DEV_DB_PORT' -U '$DEV_DB_USER' -d '$DEV_DB_NAME' -v ON_ERROR_STOP=1 -f '$REPAIR_SQL'"
  echo
  echo "说明：本检查只读执行，不会自动修改外部数据库。"
  exit 1
fi

echo "[OK] 开发库 schema 已就绪"
