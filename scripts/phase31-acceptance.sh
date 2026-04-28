#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"
ROUTE_CODE="${ROUTE_CODE:-G210}"
RUN_BUILD="${RUN_BUILD:-1}"
RUN_API="${RUN_API:-1}"

echo "============================================================"
echo "SRMP Phase31 一期收口验收"
echo "ROOT_DIR=$ROOT_DIR"
echo "BASE_URL=$BASE_URL"
echo "TENANT_ID=$TENANT_ID YEAR=$YEAR ROUTE_CODE=$ROUTE_CODE"
echo "============================================================"

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "==> Git 当前提交"
  git log --oneline -5
  echo
  if [ -n "$(git status --porcelain)" ]; then
    echo "[WARN] 当前工作区存在未提交变更："
    git status --short
    echo
  fi
fi

echo "==> 源码一致性检查"
./scripts/check-phase31-source-consistency.sh

echo "==> 仓库补丁残留检查"
./scripts/check-phase31-repo-clean.sh || true

echo "==> 前端源码检查"
./scripts/check-phase31-web-source.sh

if [ "$RUN_BUILD" = "1" ]; then
  echo "==> 后端 Maven 构建"
  mvn clean package -DskipTests

  echo "==> 前端构建"
  npm --prefix srmp-web-ui run build
else
  echo "[SKIP] RUN_BUILD=0，跳过构建"
fi

if [ "$RUN_API" = "1" ]; then
  echo "==> Demo 数据接口检查"
  ./scripts/check-phase31-demo-data.sh

  echo "==> GIS 接口检查"
  ./scripts/check-phase31-gis-api.sh

  echo "==> AI 地图对象接口检查"
  ./scripts/check-phase31-ai-map-object.sh
else
  echo "[SKIP] RUN_API=0，跳过接口检查"
fi

echo
echo "[OK] Phase31 一期收口验收脚本执行完成"
