#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
check_file() { [[ -f "$1" ]] || { echo "[FAIL] missing file: $1" >&2; exit 1; }; }
check_contains() { grep -q "$2" "$1" || { echo "[FAIL] $1 missing pattern: $2" >&2; exit 1; }; }
check_file "srmp-web-ui/src/utils/gisUnifiedContext.ts"
check_file "srmp-web-ui/src/views/gis/OneMap.vue"
check_file "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
check_file "srmp-web-ui/src/views/gis/components/RegionSelectionPanel.vue"
for token in MAP_REGION ROAD_ROUTE ROAD_SECTION EVALUATION_UNIT DISEASE ASSESSMENT_RESULT; do
  check_contains "srmp-web-ui/src/utils/gisUnifiedContext.ts" "$token"
done
check_contains "srmp-web-ui/src/views/gis/OneMap.vue" "activeRegionContext"
check_contains "srmp-web-ui/src/views/gis/OneMap.vue" "analysisTargets"
check_contains "srmp-web-ui/src/views/gis/OneMap.vue" "handleLocateAiSource"
check_contains "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue" "区域综合分析"
check_contains "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue" "sourceMapTargetLabel"
check_contains "srmp-web-ui/src/views/gis/components/RegionSelectionPanel.vue" "用区域问 AI"
echo "[OK] Phase48 v2 区域/线路/路段/病害/评定统一上下文检查通过"
