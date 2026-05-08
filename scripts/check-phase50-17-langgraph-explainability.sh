#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message"
    echo "       file: $file"
    echo "       pattern: $pattern"
    exit 1
  fi
  echo "[OK] $message"
}

require_pattern "srmp-web-ui/src/views/agent/components/aiExecution.ts" "export interface AiExecutionSnapshot" "execution snapshot model exists"
require_pattern "srmp-web-ui/src/views/agent/components/aiExecution.ts" "toAiExecutionSnapshot" "execution normalizer exists"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "AI 执行过程" "drawer uses unified title"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "AnswerSourceAlert" "drawer renders answer source summary"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "工具与证据" "drawer renders tools and evidence"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "原始诊断 JSON" "drawer keeps raw diagnostics"
require_pattern "srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue" "llmModel" "answer source shows LLM model"
require_pattern "srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue" "retriedWithCompactPrompt" "answer source shows retry metadata"
require_pattern "srmp-web-ui/src/views/agent/AiChatPage.vue" "answerMeta" "ordinary chat stores answerMeta"
require_pattern "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue" "toolResults" "GIS chat passes tool results"
require_pattern "srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue" "answerMeta" "solution preview passes answerMeta"
require_pattern "srmp-web-ui/src/views/gis/OneMap.vue" "regionSolution\\?\\.answerMeta" "region solution passes answerMeta"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "executionDrawerVisible" "Ops page has execution drawer state"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "buildReplayCompare" "Ops page has replay comparison"

npm --prefix srmp-web-ui run build

echo "[OK] Phase50.17 LangGraph explainability checks passed"
