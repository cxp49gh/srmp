#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ONE_MAP="$ROOT_DIR/srmp-web-ui/src/views/gis/OneMap.vue"
TOOLBAR="$ROOT_DIR/srmp-web-ui/src/views/gis/components/MapToolbar.vue"
LEFT="$ROOT_DIR/srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue"
DOCK="$ROOT_DIR/srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue"
AGENT="$ROOT_DIR/srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"

require_file() {
  [[ -f "$1" ]] || { echo "[FAIL] Missing file: $1" >&2; exit 1; }
}

require_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -q "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    echo "       file: $file" >&2
    echo "       pattern: $pattern" >&2
    exit 1
  fi
}

require_not_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if grep -q "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    echo "       file: $file" >&2
    echo "       pattern: $pattern" >&2
    exit 1
  fi
}

require_file "$ONE_MAP"
require_file "$TOOLBAR"
require_file "$LEFT"
require_file "$DOCK"
require_file "$AGENT"

require_contains "$TOOLBAR" "矩形框选" "顶部查询栏应包含矩形框选入口"
require_contains "$TOOLBAR" "多边形框选" "顶部查询栏应包含多边形框选入口"
require_contains "$TOOLBAR" "清除框选" "顶部查询栏应包含清除框选入口"
require_contains "$ONE_MAP" "LegendPanel class=\"map-legend-fixed\"" "图例应固定回地图左下角"
require_contains "$ONE_MAP" "MapAnalysisDock" "一张图分析应收口为底部扁平面板"
require_not_contains "$ONE_MAP" "GisRightWorkbench" "右侧一张图分析高卡片不应继续占位"
require_contains "$LEFT" "图层统计" "统计应与图层控制合并在左侧资源面板"
require_not_contains "$LEFT" "el-tab-pane label=\"图例\"" "图例不应再作为左侧资源页签"
require_not_contains "$LEFT" "el-tab-pane label=\"区域\"" "区域框选不应再作为左侧资源页签"
require_contains "$DOCK" "map-analysis-dock" "底部分析面板样式缺失"
require_contains "$AGENT" "compact-context" "AI 助手应使用紧凑上下文区"
require_contains "$AGENT" "数据源与工具" "AI 助手数据源与工具应折叠展示"
require_contains "$AGENT" "快捷提问" "AI 助手快捷提问应折叠展示"
require_not_contains "$AGENT" "当前地图对象上下文" "AI 助手不应再展示大块对象上下文标题"

echo "[OK] Phase49B 一张图布局细节收口与 AI 助手瘦身结构检查通过"
