#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

ONE_MAP="srmp-web-ui/src/views/gis/OneMap.vue"
FLOAT="srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"

echo "==> 检查 OneMap.vue 地图对象上下文传递"
grep -q "selectedMapObject" "$ONE_MAP" || { echo "[FAIL] OneMap.vue 缺少 selectedMapObject"; exit 1; }
grep -q "selectedFeatureProperties" "$ONE_MAP" || echo "[WARN] OneMap.vue 缺少 selectedFeatureProperties，详情覆盖后可能丢失原始 feature 字段"
grep -q "pendingAiQuestion" "$ONE_MAP" || echo "[WARN] OneMap.vue 缺少 pendingAiQuestion，AI分析此对象可能不会自动发送"
grep -Eq ':map-object="selectedMapObject"|:mapObject="selectedMapObject"' "$ONE_MAP" || echo "[WARN] OneMap.vue 可能未将 selectedMapObject 作为 :map-object 传给 AgentChatFloat"
grep -q "auto-question" "$ONE_MAP" || echo "[WARN] OneMap.vue 可能未传 auto-question"

echo "==> 检查 AgentChatFloat.vue 当前地图上下文展示"
grep -q "activeMapObject" "$FLOAT" || { echo "[FAIL] AgentChatFloat.vue 缺少 activeMapObject"; exit 1; }
grep -q "mapContextLabel" "$FLOAT" || echo "[WARN] AgentChatFloat.vue 缺少 mapContextLabel"
grep -q "当前地图上下文" "$FLOAT" || echo "[WARN] AgentChatFloat.vue 未展示 当前地图上下文 卡片"
grep -q "重新分析当前对象" "$FLOAT" || echo "[WARN] AgentChatFloat.vue 缺少 重新分析当前对象 按钮"
grep -q "生成处置建议" "$FLOAT" || echo "[WARN] AgentChatFloat.vue 缺少 生成处置建议 按钮"
grep -q "mapObject: activeMapObject.value" "$FLOAT" || echo "[WARN] /api/agent/chat 请求体可能未使用 activeMapObject.value"
grep -q "autoQuestion" "$FLOAT" || echo "[WARN] AgentChatFloat.vue 缺少 autoQuestion 自动触发能力"

echo "[OK] 前端源码检查完成"
