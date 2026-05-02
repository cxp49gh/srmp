#!/usr/bin/env bash
set -euo pipefail
grep -q "@EnableScheduling" srmp-admin/src/main/java/com/smartroad/srmp/admin/SmartRoadApplication.java || { echo "[FAIL] SmartRoadApplication 未开启 @EnableScheduling"; exit 1; }
grep -q "OutlineAutoSyncScheduler" srmp-agent/src/main/java/com/smartroad/srmp/agent/outline/schedule/OutlineAutoSyncScheduler.java || { echo "[FAIL] 缺少 OutlineAutoSyncScheduler"; exit 1; }
grep -q "/api/outline/auto-sync" srmp-agent/src/main/java/com/smartroad/srmp/agent/outline/controller/OutlineAutoSyncController.java || { echo "[FAIL] 缺少 OutlineAutoSyncController"; exit 1; }
grep -q "outline_auto_sync_config" srmp-admin/src/main/resources/db/phase39_3_outline_auto_sync_webhook.sql || { echo "[FAIL] 缺少 DB migration"; exit 1; }
grep -q "getOutlineAutoSyncConfigs" srmp-web-ui/src/api/outline.ts || { echo "[FAIL] outline.ts 缺少自动同步 API"; exit 1; }
grep -q "OutlineAutoSyncPage" srmp-web-ui/src/router/index.ts || { echo "[FAIL] 路由未接入 OutlineAutoSyncPage"; exit 1; }
echo "[OK] Phase39.3 结构检查通过"
