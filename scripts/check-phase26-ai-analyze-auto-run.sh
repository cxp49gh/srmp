#!/usr/bin/env bash
set -euo pipefail

grep -q "pendingAiQuestion" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "auto-question" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "autoQuestion" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "watch(" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "activeMapObject" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue

echo "[OK] 前端 AI分析此对象 自动触发代码已存在"
