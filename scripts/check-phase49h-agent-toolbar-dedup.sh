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

if grep -q "agent-open \.top-toolbar" "$ONE_MAP"; then
  echo "[FAIL] AI 打开时顶部查询栏不应再随右侧助手缩放或让位" >&2
  exit 1
fi

grep -q "flex-wrap: nowrap" "$TOOLBAR" || { echo "[FAIL] 查询条件控件应默认保持一行" >&2; exit 1; }
grep -q "区域工具" "$TOOLBAR" || { echo "[FAIL] 区域框选工具应收敛为下拉入口" >&2; exit 1; }

if grep -q "{{ contextText }}" "$AGENT"; then
  echo "[FAIL] AI 助手标题栏不应重复展示当前对象描述" >&2
  exit 1
fi

grep -q "一张图分析、养护建议与依据追踪" "$AGENT" || { echo "[FAIL] AI 助手标题栏应使用通用说明" >&2; exit 1; }
grep -q "analysis-workbench" "$AGENT" || { echo "[FAIL] 一张图分析应整合进右侧 AI 工作台" >&2; exit 1; }
grep -q "已接入指标专题" "$AGENT" || { echo "[FAIL] 当前对象摘要应保留为非重复分析说明" >&2; exit 1; }

if grep -q "MapAnalysisDock" "$ONE_MAP"; then
  echo "[FAIL] 底部一张图分析 Dock 应已取消" >&2
  exit 1
fi

echo "[OK] Phase49H 顶部查询栏固定一行与 AI 对象描述去重结构检查通过"
