#!/usr/bin/env bash
set -euo pipefail

# AI / Outline 数据库一键初始化脚本
#
# 默认参数：
#   DB_HOST=127.0.0.1
#   DB_PORT=5432
#   DB_USER=srmp
#   DB_NAME=srmp
#
# 可通过环境变量覆盖：
#   DB_HOST=127.0.0.1 DB_PORT=5432 DB_USER=srmp DB_NAME=srmp ./scripts/init-ai-db.sh
#
# 如果需要密码：
#   export PGPASSWORD=你的密码

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-srmp}"
DB_NAME="${DB_NAME:-srmp}"

PSQL_BIN="${PSQL_BIN:-psql}"

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
    echo "[WARN] SQL 文件不存在，跳过：$file"
    return 0
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

echo "==> 初始化 AI / Outline 数据库"
echo "    host=$DB_HOST"
echo "    port=$DB_PORT"
echo "    user=$DB_USER"
echo "    db=$DB_NAME"
echo ""

run_sql_file "srmp-admin/src/main/resources/db/phase17_outline_sync.sql" "阶段十七：Outline 同步任务表"
run_sql_file "srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql" "阶段三十六：AI 向量知识库"
run_sql_file "srmp-admin/src/main/resources/db/phase37_1_knowledge_reindex.sql" "阶段三十七：知识库 Reindex 元数据"
run_sql_file "srmp-admin/src/main/resources/db/phase39_2_2_outline_sync_contract_vector_closure.sql" "阶段三十九：Outline 同步契约与向量闭环"
run_sql_file "srmp-admin/src/main/resources/db/phase39_3_outline_auto_sync_webhook.sql" "阶段三十九：Outline 自动同步与 Webhook"
run_sql_file "srmp-admin/src/main/resources/db/phase41_outline_vectorize_ops.sql" "阶段四十一：Outline 向量补齐运维索引"
run_sql_file "srmp-admin/src/main/resources/db/phase42_llm_timeout_outline_auto_sync_closure.sql" "阶段四十二：AI 超时与 Outline 自动同步收口"

echo ""
echo "==> 初始化完成"
echo "建议继续执行："
echo "    ./scripts/check-db-schema.sh"
