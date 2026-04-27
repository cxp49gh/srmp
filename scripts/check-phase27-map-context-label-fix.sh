#!/usr/bin/env bash
set -euo pipefail

grep -q "selectedFeatureProperties" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "selectedMapObject" srmp-web-ui/src/views/gis/OneMap.vue
grep -q ":map-object" srmp-web-ui/src/views/gis/OneMap.vue
grep -q "mapContextLabel" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "mapObjectTypeLabel" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "病害" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "评定结果" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "mapObject: activeMapObject.value" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue

echo "[OK] 当前地图上下文标签修复点已存在"
