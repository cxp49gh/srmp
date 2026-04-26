#!/usr/bin/env bash
set -euo pipefail

# AI / Outline 启动前置检查
# 包含：
# 1. 数据库表结构检查
# 2. AI 知识库接口检查
# 3. Outline 状态检查

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

echo "==> Step 1: 检查数据库表结构"
./scripts/check-db-schema.sh

echo ""
echo "==> Step 2: 检查 AI 知识库接口"
if [ -x "./scripts/check-ai-knowledge.sh" ]; then
  BASE_URL="$BASE_URL" TENANT_ID="$TENANT_ID" ./scripts/check-ai-knowledge.sh
else
  echo "[WARN] scripts/check-ai-knowledge.sh 不存在，跳过接口验收"
fi

echo ""
echo "==> Step 3: 检查 Outline 状态"
if [ -x "./scripts/check-outline.sh" ]; then
  BASE_URL="$BASE_URL" TENANT_ID="$TENANT_ID" ./scripts/check-outline.sh
else
  echo "[WARN] scripts/check-outline.sh 不存在，跳过 Outline 验收"
fi

echo ""
echo "==> 启动前置检查完成"
