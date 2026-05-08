#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-srmp}"
DB_NAME="${DB_NAME:-srmp}"
PSQL_BIN="${PSQL_BIN:-psql}"
FULL_SQL="${FULL_SQL:-$ROOT_DIR/srmp-admin/src/main/resources/db/srmp_full_init.sql}"

"$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
  -f "$FULL_SQL"

echo "[OK] AI 方案模板相关表已随全量 SQL 初始化完成"
