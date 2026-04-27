#!/usr/bin/env bash
set -euo pipefail

grep -q "selectedMapObject" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "pendingAiQuestion" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "auto-question" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "activeMapObject" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "当前地图上下文" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "重新分析当前对象" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "生成处置建议" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "mapObject: activeMapObject.value" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue

echo "[OK] /gis/one-map 当前地图上下文前端链路已修复"
