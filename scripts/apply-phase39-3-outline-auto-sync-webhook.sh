#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
python3 scripts/patch-phase39-3-enable-scheduling.py
python3 scripts/patch-phase39-3-outline-api.py
python3 scripts/patch-phase39-3-outline-auto-sync-router.py
echo "[OK] Phase39.3 Outline 自动同步与 Webhook patch 已应用"
echo "请执行数据库脚本：srmp-admin/src/main/resources/db/phase39_3_outline_auto_sync_webhook.sql"
