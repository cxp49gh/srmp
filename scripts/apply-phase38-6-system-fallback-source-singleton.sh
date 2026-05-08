#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
python3 scripts/patch-phase38-6-system-fallback-source-singleton.py
echo "[OK] Phase38.6 系统兜底模板来源单例化 patch 已应用"
echo "建议执行数据库脚本："
echo "psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/srmp_full_init.sql"
