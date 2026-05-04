#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ONE="$ROOT_DIR/srmp-web-ui/src/views/gis/OneMap.vue"

need_file() {
  if [ ! -f "$ROOT_DIR/$1" ]; then
    echo "[FAIL] missing file: $1" >&2
    exit 1
  fi
}

need_grep() {
  local pattern="$1"
  local file="$2"
  if ! grep -q "$pattern" "$ROOT_DIR/$file"; then
    echo "[FAIL] missing pattern '$pattern' in $file" >&2
    exit 1
  fi
}

need_file srmp-web-ui/src/views/gis/OneMap.vue
need_grep "map-zoom-panel" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "级别 {{ currentZoom }}" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "attributionControl: false" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "openFeaturePopup" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "map-object-popup" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "leaflet-control-attribution" srmp-web-ui/src/views/gis/OneMap.vue
need_grep "gis-left-workbench.collapsed" srmp-web-ui/src/views/gis/OneMap.vue

if grep -q "L.control.zoom" "$ONE"; then
  echo "[FAIL] default Leaflet zoom control should be removed" >&2
  exit 1
fi

if grep -q "OpenStreetMap contributors" "$ONE"; then
  echo "[FAIL] Leaflet attribution text should not be displayed from OneMap" >&2
  exit 1
fi

echo "[OK] Phase49M 左侧收起、对象弹框、缩放级别与 Attribution 收口结构检查通过"
