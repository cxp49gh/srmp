#!/usr/bin/env bash
set -euo pipefail

ONE_MAP="srmp-web-ui/src/views/gis/OneMap.vue"
AGENT_FLOAT="srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
DETAIL_DRAWER="srmp-web-ui/src/views/gis/components/ObjectDetailDrawer.vue"
LAYER_DRAWER="srmp-web-ui/src/views/gis/components/LayerDrawer.vue"
CLIPBOARD="srmp-web-ui/src/utils/clipboard.ts"

grep -q "layerStatus" "$ONE_MAP"
grep -q "getCurrentViewport" "$ONE_MAP"
grep -q "copySelectedContext" "$ONE_MAP"
grep -q "locateSelectedObject" "$ONE_MAP"
grep -q "selectedLayers: activeLayerNames" "$ONE_MAP"
grep -q "__srmpFeatureId" "$ONE_MAP"
grep -q "@fit=\"handleFitAll\"" "$ONE_MAP"

grep -q "刷新图层" "$LAYER_DRAWER"
grep -q "加载异常" "$LAYER_DRAWER"
grep -q "LayerLoadStatus" "$LAYER_DRAWER"

grep -q "复制上下文" "$DETAIL_DRAWER"
grep -q "定位对象" "$DETAIL_DRAWER"
grep -q "AI 上下文" "$DETAIL_DRAWER"

grep -q "mapAgentChat" "$AGENT_FLOAT"
grep -q "mapContext: buildMapAiContext" "$AGENT_FLOAT" || grep -q "mapContext" "$AGENT_FLOAT"
grep -q "已使用地图上下文" "$AGENT_FLOAT"
grep -q "未确认地图上下文" "$AGENT_FLOAT"
grep -q "知识库检索" "$AGENT_FLOAT"
grep -q "【基于当前地图对象】" "$AGENT_FLOAT"
grep -q "copyCurrentMapContext" "$AGENT_FLOAT"

grep -q "copyText" "$CLIPBOARD"

echo "[OK] Phase47 GIS 一张图 + AI 地图上下文闭环前端结构检查通过"
