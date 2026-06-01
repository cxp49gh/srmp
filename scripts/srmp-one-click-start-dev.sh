#!/usr/bin/env bash
#
# SRMP 开发环境一键启动脚本（使用已有的 minio、outline、postgresql、redis）
#
# 特点：
# - 前后端都使用 Docker 方式
# - 不初始化数据库（复用已有数据）
# - 每次启动都重新构建代码
#
# 用法:
#   ./scripts/srmp-one-click-start-dev.sh                    # 启动全部服务
#   ./scripts/srmp-one-click-start-dev.sh --no-orchestrator  # 不启动 AI orchestrator
#   ./scripts/srmp-one-click-start-dev.sh --help             # 显示帮助
#
# 配置：
#   复制 .env.dev.example 为 .env.dev，填入实际的服务地址
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

NO_ORCHESTRATOR=0
SKIP_SCHEMA_CHECK=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-orchestrator)
      NO_ORCHESTRATOR=1
      shift
      ;;
    --skip-schema-check)
      SKIP_SCHEMA_CHECK=1
      shift
      ;;
    --help)
      cat <<'USAGE'
Usage: ./scripts/srmp-one-click-start-dev.sh [options]

Options:
  --no-orchestrator  Do not start bundled srmp-ai-orchestrator.
  --skip-schema-check Skip read-only external database schema preflight.
  --help             Show this help.

Configuration:
  Set external service addresses in .env.dev (copy from .env.dev.example).
USAGE
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] missing command: $1"
    exit 1
  }
}

# 加载 .env.dev（如果存在）
if [ -f "$ROOT_DIR/.env.dev" ]; then
  echo "[INFO] Loading .env.dev"
  set -a
  source "$ROOT_DIR/.env.dev"
  set +a
fi

# 默认值
: "${DEV_DB_HOST:=127.0.0.1}"
: "${DEV_DB_PORT:=5432}"
: "${DEV_DB_NAME:=srmp}"
: "${DEV_DB_USER:=srmp}"
: "${DEV_DB_PASSWORD:=srmp123}"
: "${DEV_REDIS_HOST:=127.0.0.1}"
: "${DEV_REDIS_PORT:=6379}"
: "${DEV_MINIO_ENDPOINT:=http://127.0.0.1:9000}"
: "${DEV_MINIO_ACCESS_KEY:=minioadmin}"
: "${DEV_MINIO_SECRET_KEY:=minioadmin}"
: "${DEV_MINIO_BUCKET:=srmp}"

# 导出环境变量覆盖 docker-compose 中的默认值
export DB_HOST="$DEV_DB_HOST"
export DB_PORT="$DEV_DB_PORT"
export DB_NAME="$DEV_DB_NAME"
export DB_USER="$DEV_DB_USER"
export DB_PASSWORD="$DEV_DB_PASSWORD"
export REDIS_HOST="$DEV_REDIS_HOST"
export REDIS_PORT="$DEV_REDIS_PORT"
export MINIO_ENDPOINT="$DEV_MINIO_ENDPOINT"
export MINIO_ACCESS_KEY="$DEV_MINIO_ACCESS_KEY"
export MINIO_SECRET_KEY="$DEV_MINIO_SECRET_KEY"
export MINIO_BUCKET="$DEV_MINIO_BUCKET"

# LLM 相关（可选）
export SRMP_LLM_API_KEY="${SRMP_LLM_API_KEY:-}"
export SRMP_LLM_BASE_URL="${SRMP_LLM_BASE_URL:-}"
export SRMP_LLM_MODEL="${SRMP_LLM_MODEL:-}"
export SRMP_EMBEDDING_ENDPOINT="${SRMP_EMBEDDING_ENDPOINT:-}"
export SRMP_EMBEDDING_MODEL="${SRMP_EMBEDDING_MODEL:-}"

# Outline 相关（可选）
export OUTLINE_ENABLED="${OUTLINE_ENABLED:-false}"
export OUTLINE_BASE_URL="${OUTLINE_BASE_URL:-}"
export OUTLINE_API_TOKEN="${OUTLINE_API_TOKEN:-}"
export OUTLINE_DEFAULT_COLLECTION_ID="${OUTLINE_DEFAULT_COLLECTION_ID:-}"

echo "==> 开发环境一键启动（使用外部服务）"
echo "    PostgreSQL: $DEV_DB_HOST:$DEV_DB_PORT/$DEV_DB_NAME"
echo "    Redis:      $DEV_REDIS_HOST:$DEV_REDIS_PORT"
echo "    MinIO:      $DEV_MINIO_ENDPOINT"

require_cmd docker

if [ "${DEV_SCHEMA_PREFLIGHT:-1}" = "1" ] && [ "$SKIP_SCHEMA_CHECK" = "0" ]; then
  ./scripts/srmp-check-dev-schema.sh --no-env
else
  echo "[INFO] Skipping dev database schema preflight"
fi

# 确定 compose 文件
if [ "$NO_ORCHESTRATOR" = "1" ]; then
  compose_files="-f docker-compose.yml -f docker-compose.app.yml -f docker-compose.dev.yml"
  services="backend frontend"
else
  compose_files="-f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml -f docker-compose.dev.yml"
  services="backend frontend srmp-ai-orchestrator"
fi

echo "==> 启动服务（跳过 postgres/redis/minio 依赖）: $services"
# --no-deps: 不启动 depends_on 中定义的服务（使用外部服务）
docker compose $compose_files up -d --no-deps --build $services

echo
echo "[OK] SRMP 开发环境启动完成"
echo "Frontend:  http://localhost:${FRONTEND_PORT:-5173}"
echo "Backend:   http://localhost:8080"
if [ "$NO_ORCHESTRATOR" = "0" ]; then
  echo "LangGraph: http://localhost:18080"
fi
echo
echo "查看日志: docker compose $compose_files logs -f"
