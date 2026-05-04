#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMP="$ROOT_DIR/srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"

require() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    exit 1
  fi
}
forbid() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    exit 1
  fi
}

require "analyzeObjectLabel" "$COMP" "缺少对象类型化分析按钮"
require "primarySolutionAction" "$COMP" "缺少主生成动作收口"
require "secondarySolutionActions" "$COMP" "缺少次级生成动作收口"
require "analysis-action-hint" "$COMP" "缺少操作语义说明"
require "生成低分处置建议" "$COMP" "评定结果缺少低分处置建议文案"
require "生成单元养护建议" "$COMP" "评定单元缺少单元养护建议文案"
require "生成路段养护计划" "$COMP" "路段缺少养护计划文案"
require "生成路线养护报告" "$COMP" "路线缺少养护报告文案"
require "生成结构化建议" "$COMP" "AI 回答后的结构化建议按钮文案未收口"
forbid "command=\"suggest-object\"" "$COMP" "更多操作里仍存在固定的生成处置建议入口"
forbid "基于本次分析生成方案草稿" "$COMP" "仍存在容易误解的生成方案草稿文案"

echo "[OK] Phase49P AI 养护助手操作语义收口检查通过"
