#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ONE_MAP="$ROOT_DIR/srmp-web-ui/src/views/gis/OneMap.vue"
TOOLBAR="$ROOT_DIR/srmp-web-ui/src/views/gis/components/MapToolbar.vue"

require() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    exit 1
  fi
}

require "evaluationUnit: true" "$ONE_MAP" "评定单元图层应默认勾选"
require "const agentVisible = ref\(true\)" "$ONE_MAP" "AI 养护助手应默认打开"
require "<svg class=\"region-icon region-rect\"" "$TOOLBAR" "矩形框选应使用 SVG 图标"
require "<svg class=\"region-icon region-poly\"" "$TOOLBAR" "多边形框选应使用 SVG 图标"
require "<svg class=\"region-icon region-clear\"" "$TOOLBAR" "清除框选应使用 SVG 图标"
require "region-poly circle" "$TOOLBAR" "多边形图标应带顶点样式"

if grep -q "<span class=\"region-icon" "$TOOLBAR"; then
  echo "[FAIL] 框选图标不应继续使用文字 span" >&2
  exit 1
fi

echo "[OK] Phase49L AI 默认打开、评定单元默认勾选与框选图标检查通过"
