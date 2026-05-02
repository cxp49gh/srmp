#!/usr/bin/env bash
set -euo pipefail

echo "==> 检查 Outline 同步链路是否仍写 legacy knowledge_document / knowledge_chunk"
if grep -R "knowledge_document\|knowledge_chunk" srmp-agent/src/main/java/com/smartroad/srmp/agent/outline srmp-web-ui/src/api/outline.ts srmp-web-ui/src/views/agent/OutlineSyncPage.vue; then
  echo "[FAIL] Outline 同步链路仍引用 legacy knowledge_document / knowledge_chunk"
  exit 1
fi

grep -R "aiKnowledgeIngestService.ingestMarkdown" srmp-agent/src/main/java/com/smartroad/srmp/agent/outline/service/impl/OutlineSyncServiceImpl.java   || { echo "[FAIL] Outline 同步未走 AiKnowledgeIngestService"; exit 1; }

echo "[OK] Outline 同步只走 ai_knowledge_* 新知识库链路"
