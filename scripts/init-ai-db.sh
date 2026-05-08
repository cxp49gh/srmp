#!/usr/bin/env bash
set -euo pipefail

# AI / Outline 数据库初始化：与一键启动相同，执行全量 srmp_full_init.sql（幂等）。
# 环境变量与 scripts/srmp-init-demo.sh 一致时可设置 FULL_SQL 覆盖路径。

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-srmp}"
DB_NAME="${DB_NAME:-srmp}"

PSQL_BIN="${PSQL_BIN:-psql}"
FULL_SQL="${FULL_SQL:-$ROOT_DIR/srmp-admin/src/main/resources/db/srmp_full_init.sql}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] 缺少命令：$1"
    echo "请先安装 PostgreSQL 客户端，或设置 PSQL_BIN=/path/to/psql"
    exit 1
  }
}

run_sql_file() {
  local file="$1"
  local name="$2"

  if [ ! -f "$file" ]; then
    echo "[FAIL] SQL 文件不存在：$file"
    exit 1
  fi

  echo "==> 执行 $name"
  "$PSQL_BIN" \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 \
    -f "$file"

  echo "[OK] $name"
}

require_cmd "$PSQL_BIN"

echo "==> 初始化 AI / Outline 数据库（全量 SQL）"
echo "    host=$DB_HOST"
echo "    port=$DB_PORT"
echo "    user=$DB_USER"
echo "    db=$DB_NAME"
echo "    file=$FULL_SQL"
echo ""

run_sql_file "$FULL_SQL" "SRMP 全量初始化（含 AI/Outline）"

echo ""
echo "==> 初始化完成"
echo "建议继续执行："
echo "    ./scripts/check-db-schema.sh"
