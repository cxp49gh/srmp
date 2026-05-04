#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHAT="$ROOT/srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
EVIDENCE="$ROOT/srmp-web-ui/src/views/gis/components/AiEvidencePanel.vue"
ONEMAP="$ROOT/srmp-web-ui/src/views/gis/OneMap.vue"
LEFT="$ROOT/srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue"
DOCK="$ROOT/srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue"
CTX="$ROOT/srmp-web-ui/src/utils/gisUnifiedContext.ts"

for file in "$CHAT" "$EVIDENCE" "$ONEMAP" "$LEFT" "$DOCK" "$CTX"; do
  [[ -f "$file" ]] || { echo "[FAIL] missing $file"; exit 1; }
done

grep -q "依据与调用" "$EVIDENCE" || { echo "[FAIL] AiEvidencePanel 未统一为依据与调用"; exit 1; }
grep -q "参考资料与地图关联" "$EVIDENCE" || { echo "[FAIL] AiEvidencePanel 未承载参考资料与地图关联"; exit 1; }
grep -q "@locate-source=\"locateEvidenceSource\"" "$CHAT" || { echo "[FAIL] AgentChatFloat 未接入统一依据定位事件"; exit 1; }
if grep -q "参考资料 / 地图关联" "$CHAT"; then
  echo "[FAIL] AgentChatFloat 仍存在重复的参考资料 / 地图关联面板"
  exit 1
fi
if grep -q "<div v-if=\"item.role === 'assistant' && item.toolResults" "$CHAT"; then
  echo "[FAIL] AgentChatFloat 仍存在重复的工具调用面板"
  exit 1
fi
grep -q "bboxToBounds" "$ONEMAP" || { echo "[FAIL] OneMap 未修复 bbox 定位兼容"; exit 1; }
grep -q "stakeToNumber" "$ONEMAP" || { echo "[FAIL] OneMap 未增强桩号匹配"; exit 1; }
grep -q "normalizeIncomingMapTarget" "$ONEMAP" || { echo "[FAIL] OneMap 未兼容已归一化的地图目标"; exit 1; }
grep -q "mapTarget" "$CTX" || { echo "[FAIL] GIS 统一上下文未兼容 mapTarget 字段"; exit 1; }
grep -q "bottom: 328px" "$LEFT" || { echo "[FAIL] 左侧资源面板未给图例和分析 Dock 留足空间"; exit 1; }
grep -q "width: min(760px" "$DOCK" || { echo "[FAIL] 一张图分析 Dock 未收窄"; exit 1; }

echo "[OK] Phase49F 一张图布局与 AI 依据/地图定位收口检查通过"
