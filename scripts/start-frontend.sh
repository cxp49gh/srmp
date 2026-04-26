#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/srmp-web-ui"

echo "==> 安装前端依赖"
npm install

echo "==> 启动前端"
npm run dev
