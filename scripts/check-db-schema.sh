#!/usr/bin/env bash
set -euo pipefail

# 数据库表结构检查脚本
#
# 默认参数：
#   DB_HOST=127.0.0.1
#   DB_PORT=5432
#   DB_USER=srmp
#   DB_NAME=srmp
#
# 可通过环境变量覆盖：
#   DB_HOST=127.0.0.1 DB_PORT=5432 DB_USER=srmp DB_NAME=srmp ./scripts/check-db-schema.sh
#
# 如果需要密码：
#   export PGPASSWORD=你的密码

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-srmp}"
DB_NAME="${DB_NAME:-srmp}"

PSQL_BIN="${PSQL_BIN:-psql}"

REQUIRED_TABLES=(
  "ai_knowledge_document"
  "ai_knowledge_chunk"
  "outline_sync_task"
  "road_route"
  "road_section"
  "evaluation_unit"
  "disease_record"
  "assessment_result"
)

AI_REQUIRED_TABLES=(
  "ai_knowledge_document"
  "ai_knowledge_chunk"
  "outline_sync_task"
)

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] 缺少命令：$1"
    echo "请先安装 PostgreSQL 客户端，或设置 PSQL_BIN=/path/to/psql"
    exit 1
  }
}

table_exists() {
  local table="$1"
  local result
  result="$("$PSQL_BIN" \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -tAc "select to_regclass('public.${table}') is not null;" 2>/dev/null || true)"

  [ "$result" = "t" ]
}

check_columns() {
  local table="$1"
  shift
  local missing=0

  for column in "$@"; do
    local result
    result="$("$PSQL_BIN" \
      -h "$DB_HOST" \
      -p "$DB_PORT" \
      -U "$DB_USER" \
      -d "$DB_NAME" \
      -tAc "select exists(select 1 from information_schema.columns where table_schema='public' and table_name='${table}' and column_name='${column}');" 2>/dev/null || true)"
    if [ "$result" != "t" ]; then
      echo "    [FAIL] 缺少字段：${table}.${column}"
      missing=1
    fi
  done

  return "$missing"
}

require_cmd "$PSQL_BIN"

echo "==> 检查数据库表结构"
echo "    host=$DB_HOST"
echo "    port=$DB_PORT"
echo "    user=$DB_USER"
echo "    db=$DB_NAME"
echo ""

fail_count=0

for table in "${REQUIRED_TABLES[@]}"; do
  if table_exists "$table"; then
    echo "[OK]   $table"
  else
    echo "[FAIL] $table 不存在"
    fail_count=$((fail_count + 1))
  fi
done

echo ""
echo "==> 检查 AI / Outline 核心字段"

if table_exists "ai_knowledge_document"; then
  if ! check_columns "ai_knowledge_document" \
    "id" "tenant_id" "source_type" "source_id" "title" "status" "metadata" "content_hash" "created_at" "updated_at"; then
    fail_count=$((fail_count + 1))
  fi
fi

if table_exists "ai_knowledge_chunk"; then
  if ! check_columns "ai_knowledge_chunk" \
    "id" "tenant_id" "document_id" "source_type" "source_id" "title" "section_title" "chunk_index" "content" "metadata" "embedding" "embedding_model" "embedding_provider" "embedding_dimensions" "embedded_at"; then
    fail_count=$((fail_count + 1))
  fi
fi

if table_exists "outline_sync_task"; then
  if ! check_columns "outline_sync_task" \
    "id" "tenant_id" "collection_id" "collection_name" "sync_mode" "status" "total_count" "success_count" "skip_count" "fail_count" "error_message" "started_at" "finished_at"; then
    fail_count=$((fail_count + 1))
  fi
fi

echo ""

if [ "$fail_count" -eq 0 ]; then
  echo "==> 数据库表结构检查通过"
  exit 0
fi

echo "==> 数据库表结构检查失败，缺失数量：$fail_count"
echo ""
echo "建议先执行："
echo "    ./scripts/init-ai-db.sh"
echo ""
echo "如果基础业务表缺失，请确认是否已执行一期基础库初始化 SQL。"
exit 1
