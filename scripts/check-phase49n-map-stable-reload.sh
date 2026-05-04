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

need_grep '@change="handleLayerChange"' "$ONE"
need_grep '@reload="handleLayerRefresh"' "$ONE"
need_grep 'syncVisibleLayers' "$ONE"
need_grep 'preserveViewport' "$ONE"
need_grep 'captureViewSnapshot' "$ONE"
need_grep 'restoreViewSnapshot' "$ONE"
need_grep 'mapLoadingMask' "$ONE"
if grep -q 'handleFitAll()' < <(sed -n '/async function handleSearch/,/async function handleReset/p' "$ONE"); then
  echo "[FAIL] handleSearch should not auto fitBounds" >&2
  exit 1
fi

echo "[OK] Phase49N 地图图层切换与查询稳定性结构检查通过"
