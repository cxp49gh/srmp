#!/usr/bin/env bash
set -euo pipefail

echo "==> Step 1: AI 知识库验收"
"$(dirname "$0")/check-ai-knowledge.sh"

echo ""
echo "==> Step 2: Outline 接入验收"
"$(dirname "$0")/check-outline.sh"

echo ""
echo "==> AI 知识库与 Outline 端到端验收完成"
