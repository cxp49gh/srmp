#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

ONE_MAP="srmp-web-ui/src/views/gis/OneMap.vue"
LEFT="srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue"
RIGHT="srmp-web-ui/src/views/gis/components/GisRightWorkbench.vue"

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "[FAIL] missing file: $file" >&2
    exit 1
  fi
}

require_text() {
  local file="$1"
  local text="$2"
  if ! grep -q "$text" "$file"; then
    echo "[FAIL] $file missing marker: $text" >&2
    exit 1
  fi
}

reject_template_text() {
  local text="$1"
  if sed -n '/<template>/,/<\/template>/p' "$ONE_MAP" | grep -q "$text"; then
    echo "[FAIL] OneMap template still contains old floating entry: $text" >&2
    exit 1
  fi
}

require_file "$ONE_MAP"
require_file "$LEFT"
require_file "$RIGHT"

require_text "$ONE_MAP" "GisLeftWorkbench"
require_text "$ONE_MAP" "GisRightWorkbench"
require_text "$ONE_MAP" "rightWorkbenchTab"
require_text "$ONE_MAP" "layerCounts"
require_text "$ONE_MAP" "clearSelection"
require_text "$LEFT" "图层、图例、统计、区域工具统一入口"
require_text "$RIGHT" "对象、区域、AI 和草稿统一操作区"
require_text "$RIGHT" "生成区域建议"

reject_template_text "<LayerDrawer"
reject_template_text "<LegendPanel"
reject_template_text "<ObjectDetailDrawer"
reject_template_text "<MapStatisticsBar"
reject_template_text "<RegionSelectionPanel"
reject_template_text "region-tool"

if grep -q "const style = layerStyle(feature?.properties || feature) as any" "$ONE_MAP" && \
   [[ "$(grep -c "const style = layerStyle(feature?.properties || feature) as any" "$ONE_MAP")" -gt 1 ]]; then
  echo "[FAIL] duplicated Leaflet point style declaration" >&2
  exit 1
fi

echo "[OK] Phase49A GIS 一张图工作台布局与功能收口结构检查通过"
