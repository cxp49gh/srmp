#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ONE_MAP="$ROOT_DIR/srmp-web-ui/src/views/gis/OneMap.vue"
TOOLBAR="$ROOT_DIR/srmp-web-ui/src/views/gis/components/MapToolbar.vue"
LEFT="$ROOT_DIR/srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue"
DOCK="$ROOT_DIR/srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue"

require() {
  local file="$1"
  local pattern="$2"
  local label="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $label" >&2
    echo "       file: $file" >&2
    echo "       pattern: $pattern" >&2
    exit 1
  fi
}

require "$ONE_MAP" "agent-open" "OneMap 应根据 AI 打开状态切换布局 class"
require "$ONE_MAP" "leaflet-bottom\.leaflet-right" "OneMap 应调整 Leaflet 右下控件避让 AI 面板"
require "$TOOLBAR" "@submit\.prevent" "顶部查询表单应避免回车触发表单默认提交"
require "$TOOLBAR" "toolbar-actions" "顶部查询与框选动作应保留统一按钮组"
require "$LEFT" "bottom: 252px" "左侧资源面板应给左下图例和底部分析面板预留空间"
require "$LEFT" "scrollbar-width: thin" "左侧资源面板内容过多时应内部滚动"
require "$DOCK" "dock-bottom-row" "一张图分析应拆分摘要区和操作区"
require "$DOCK" "actionHint" "一张图分析应展示明确操作提示"
require "$DOCK" "metric-item" "一张图分析指标应以更易读卡片形式展示"
require "$DOCK" "with-agent" "一张图分析应在 AI 面板打开时自适应宽度"

echo "[OK] Phase49E 一张图布局细节调优结构检查通过"
