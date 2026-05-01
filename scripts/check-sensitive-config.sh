#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> 检查敏感配置 / API Key"

# 排除 .git、node_modules、target、上传生成包和 IDE 目录。
matches="$(grep -RInE 'sk-[A-Za-z0-9_-]{16,}|DASHSCOPE_API_KEY=[A-Za-z0-9_-]+|OPENAI_API_KEY=[A-Za-z0-9_-]+' \
  --exclude-dir=.git \
  --exclude-dir=.idea \
  --exclude-dir=node_modules \
  --exclude-dir=target \
  --exclude-dir=dist \
  --exclude='*.zip' \
  --exclude='*.patch' \
  . || true)"

if [ -n "$matches" ]; then
  echo "$matches"
  echo "[FAIL] 检测到疑似明文 API Key，请移除并改用环境变量。"
  exit 1
fi

echo "[OK] 未发现疑似明文 API Key"
