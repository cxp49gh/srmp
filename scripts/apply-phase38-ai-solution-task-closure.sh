#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/patch-phase38-solution-api.py
python3 scripts/patch-phase38-agent-chat-ai-context.py
python3 scripts/patch-phase38-solution-tasks-page.py

echo "[OK] Phase38 前端 patch 已应用"
echo "请执行数据库迁移：srmp-admin/src/main/resources/db/phase38_ai_solution_task_closure.sql"
