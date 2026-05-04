#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ONE="$ROOT_DIR/srmp-web-ui/src/views/gis/OneMap.vue"

need_grep() {
  local pattern="$1"
  local file="$2"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] missing pattern: $pattern in $file" >&2
    exit 1
  fi
}

need_grep 'normalizeFeatureProperties' "$ONE"
need_grep 'feature\?\.id' "$ONE"
need_grep 'popupRowsByType' "$ONE"
need_grep 'ROAD_ROUTE' "$ONE"
need_grep '路线编号' "$ONE"
need_grep '路段编号' "$ONE"
need_grep '路段名称' "$ONE"
need_grep '单元编号' "$ONE"
need_grep '病害类型' "$ONE"
need_grep 'directionText' "$ONE"
need_grep 'severityText' "$ONE"
need_grep 'gradeText' "$ONE"
need_grep 'quantityText' "$ONE"

if grep -q "const rows = \[" < <(sed -n '/function buildFeaturePopupHtml/,/function objectTypeText/p' "$ONE"); then
  echo "[FAIL] popup should use type-specific rows instead of one generic rows array" >&2
  exit 1
fi

echo "[OK] Phase49O 地图对象详情弹框字段归一化检查通过"
