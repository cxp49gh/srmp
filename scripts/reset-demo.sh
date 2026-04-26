#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-srmp}"
DB_USER="${DB_USER:-srmp}"

export PGPASSWORD="${PGPASSWORD:-srmp123}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> 重置 G210 演示数据"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/srmp-admin/src/main/resources/db/demo_data.sql"

if [ -f "$ROOT_DIR/srmp-admin/src/main/resources/db/check_g210_2026_data.sql" ]; then
  echo "==> 核查 G210 2026 演示数据"
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROOT_DIR/srmp-admin/src/main/resources/db/check_g210_2026_data.sql"
fi

echo "==> 演示数据重置完成"
