#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f deploy/outline/outline.env ]; then
  echo "==> 未发现 deploy/outline/outline.env，自动从 example 创建"
  cp deploy/outline/outline.env.example deploy/outline/outline.env
  echo "==> 请注意：建议修改 outline.env 中的 SECRET_KEY 和 UTILS_SECRET"
fi

mkdir -p deploy/outline/data/postgres deploy/outline/data/minio

echo "==> 启动本地 Outline"
docker compose -f docker-compose.outline.yml up -d

echo "==> Outline 正在启动，请稍后访问："
echo "    http://localhost:3000"
echo ""
echo "本地测试账号："
echo "    admin@example.com"
echo "    password"