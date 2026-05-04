#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

need_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "[FAIL] missing file: $file" >&2
    exit 1
  fi
}

need_grep() {
  local pattern="$1"
  local file="$2"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] pattern not found in $file: $pattern" >&2
    exit 1
  fi
}

need_file srmp-web-ui/src/views/gis/OneMap.vue
need_file srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue
need_file srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue
need_file srmp-web-ui/src/views/gis/components/AgentChatFloat.vue

need_grep ':agent-visible="agentVisible"' srmp-web-ui/src/views/gis/OneMap.vue
need_grep 'bottom: 216px;' srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue
need_grep 'overflow-y: auto;' srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue
need_grep "with-agent" srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue
need_grep 'min-height: 116px;' srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue
need_grep 'dock-top-row' srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue

if grep -q 'max-height: calc(100vh - 210px)' srmp-web-ui/src/views/gis/components/GisLeftWorkbench.vue; then
  echo "[FAIL] left workbench still uses old max-height and may cover legend" >&2
  exit 1
fi

if grep -q '@media (max-width: 960px) {[[:space:]]*@media' srmp-web-ui/src/views/gis/components/MapAnalysisDock.vue; then
  echo "[FAIL] duplicated nested media query found" >&2
  exit 1
fi

echo "[OK] Phase49C 一张图左侧遮挡与底部分析面板调优结构检查通过"
