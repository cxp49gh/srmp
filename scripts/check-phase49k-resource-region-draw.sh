#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ONE_MAP="$ROOT/srmp-web-ui/src/views/gis/OneMap.vue"
TOOLBAR="$ROOT/srmp-web-ui/src/views/gis/components/MapToolbar.vue"

require_contains() {
  local file="$1"
  local pattern="$2"
  local desc="$3"
  if ! grep -q "$pattern" "$file"; then
    echo "[FAIL] $desc" >&2
    echo "       missing pattern: $pattern in $file" >&2
    exit 1
  fi
  echo "[OK] $desc"
}

require_contains "$TOOLBAR" "region-icon-actions" "查询栏使用独立图标按钮承载框选工具"
require_contains "$TOOLBAR" "emitRegion('RECTANGLE')" "矩形框选按钮直接触发绘制"
require_contains "$TOOLBAR" "emitRegion('POLYGON')" "多边形框选按钮直接触发绘制"
require_contains "$TOOLBAR" "emitClearRegion" "清除框选按钮直接触发清除"
require_contains "$ONE_MAP" "region-draw-tip" "地图绘制态存在操作提示"
require_contains "$ONE_MAP" "handleRegionMouseMove" "框选支持鼠标移动预览"
require_contains "$ONE_MAP" "renderRectanglePreview" "矩形框选支持拖拽预览"
require_contains "$ONE_MAP" "renderPolygonPreview" "多边形框选支持顶点/边预览"
require_contains "$ONE_MAP" "cancelRegionDraw" "框选支持右键或 Esc 取消"
require_contains "$ONE_MAP" "top: 88px" "左侧资源面板顶部位置上移"

echo "[OK] Phase49K 一张图资源面板与框选交互优化结构检查通过"
