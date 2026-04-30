#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-srmp}"
DB_NAME="${DB_NAME:-srmp}"
PSQL_BIN="${PSQL_BIN:-psql}"

"$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
  -f srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql \
  -f srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql \
  -f srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql \
  -f srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql \
  -f srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql \
  -f srmp-admin/src/main/resources/db/phase35_sample_solution_templates.sql

echo "[OK] Phase35 样例方案模板初始化完成"
