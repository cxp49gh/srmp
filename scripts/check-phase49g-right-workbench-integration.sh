#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ONE_MAP="$ROOT_DIR/srmp-web-ui/src/views/gis/OneMap.vue"
AGENT="$ROOT_DIR/srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
TOOLBAR="$ROOT_DIR/srmp-web-ui/src/views/gis/components/MapToolbar.vue"

for file in "$ONE_MAP" "$AGENT" "$TOOLBAR"; do
  if [[ ! -f "$file" ]]; then
    echo "[FAIL] missing file: $file" >&2
    exit 1
  fi
done

if grep -q "<MapAnalysisDock" "$ONE_MAP"; then
  echo "[FAIL] OneMap.vue should not render bottom MapAnalysisDock after Phase49G" >&2
  exit 1
fi

grep -q "analysis-workbench" "$AGENT" || { echo "[FAIL] AgentChatFloat.vue should contain integrated analysis-workbench" >&2; exit 1; }
grep -q "AiEvidencePanel" "$AGENT" || { echo "[FAIL] AgentChatFloat.vue should keep unified evidence display via AiEvidencePanel" >&2; exit 1; }
grep -q "区域工具" "$TOOLBAR" || { echo "[FAIL] MapToolbar.vue should collapse region actions into 区域工具" >&2; exit 1; }
grep -q "agent-open .top-toolbar" "$ONE_MAP" || { echo "[FAIL] OneMap.vue should make top toolbar give space to right workbench" >&2; exit 1; }

echo "[OK] Phase49G 一张图分析与 AI 右侧统一工作台结构检查通过"
