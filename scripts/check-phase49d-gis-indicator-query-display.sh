#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

need_file() {
  [[ -f "$1" ]] || { echo "[FAIL] missing file: $1" >&2; exit 1; }
}

need_grep() {
  local pattern="$1"
  local file="$2"
  grep -q "$pattern" "$file" || { echo "[FAIL] missing pattern '$pattern' in $file" >&2; exit 1; }
}

need_file srmp-web-ui/src/utils/roadConditionMetrics.ts
need_file srmp-web-ui/src/utils/leafletStyle.ts
need_file srmp-web-ui/src/views/gis/OneMap.vue
need_file srmp-web-ui/src/views/gis/components/MapToolbar.vue
need_file srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue
need_file srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue
need_file srmp-web-ui/src/views/gis/components/LegendPanel.vue

need_grep "ROAD_CONDITION_METRICS" srmp-web-ui/src/utils/roadConditionMetrics.ts
need_grep "PSSI" srmp-web-ui/src/utils/roadConditionMetrics.ts
need_grep "gradeFromScore" srmp-web-ui/src/utils/roadConditionMetrics.ts
need_grep "getMetricGrade" srmp-web-ui/src/utils/leafletStyle.ts
need_grep "indexCode" srmp-web-ui/src/views/gis/components/MapToolbar.vue
need_grep "等级" srmp-web-ui/src/views/gis/components/MapToolbar.vue
need_grep "metric-hint" srmp-web-ui/src/views/gis/components/MapToolbar.vue
need_grep "async function handleSearch(nextQuery" srmp-web-ui/src/views/gis/OneMap.vue
need_grep ":index-code=\"query.indexCode\"" srmp-web-ui/src/views/gis/OneMap.vue
need_grep ":query=\"query\"" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "activeMetricValue" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "平均" srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue
need_grep "metric-badge" srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue
need_grep "等级" srmp-web-ui/src/views/gis/components/LegendPanel.vue

echo "[OK] Phase49D GIS 指标查询与展示结构检查通过"
