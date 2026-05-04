#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

ONE_MAP="srmp-web-ui/src/views/gis/OneMap.vue"
TOOLBAR="srmp-web-ui/src/views/gis/components/MapToolbar.vue"

[[ -f "$ONE_MAP" ]] || { echo "[FAIL] missing $ONE_MAP"; exit 1; }
[[ -f "$TOOLBAR" ]] || { echo "[FAIL] missing $TOOLBAR"; exit 1; }

grep -q 'left-map-stack' "$ONE_MAP" || { echo "[FAIL] left resource and legend stack not found"; exit 1; }
grep -q 'LegendPanel class="map-legend-fixed"' "$ONE_MAP" || { echo "[FAIL] legend panel missing in left stack"; exit 1; }
grep -q 'width: min(960px, calc(100vw - 592px))' "$ONE_MAP" || { echo "[FAIL] top toolbar compact reserved width missing"; exit 1; }
! grep -q '@click="\$emit('\''fit'\'')"' "$TOOLBAR" || { echo "[FAIL] toolbar still exposes full-map button"; exit 1; }
! grep -q 'metric-hint' "$TOOLBAR" || { echo "[FAIL] metric hint should be removed from toolbar"; exit 1; }
grep -q 'width: 110px' "$TOOLBAR" || { echo "[FAIL] route input compact width missing"; exit 1; }
grep -q 'width: 206px' "$TOOLBAR" || { echo "[FAIL] metric select compact width missing"; exit 1; }

echo "[OK] Phase49I 查询栏与左侧图例合并调优结构检查通过"
