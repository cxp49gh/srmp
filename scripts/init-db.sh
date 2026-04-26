#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-srmp}"
DB_USER="${DB_USER:-srmp}"

export PGPASSWORD="${PGPASSWORD:-srmp123}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 初始化数据库：${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/srmp-admin/src/main/resources/db/schema.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/srmp-admin/src/main/resources/db/init_dict.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/srmp-admin/src/main/resources/db/init_admin.sql"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/srmp-admin/src/main/resources/db/demo_data.sql"

echo "==> 数据库初始化完成"
