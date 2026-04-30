#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> 检查 Phase36 后端文件"
test -f srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql
grep -q "CREATE EXTENSION IF NOT EXISTS vector" srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql
grep -q "CREATE TABLE IF NOT EXISTS ai_knowledge_document" srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql
grep -q "CREATE TABLE IF NOT EXISTS ai_knowledge_chunk" srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql

test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/embedding/EmbeddingClient.java
test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/knowledge/service/AiKnowledgeRetrieverService.java
test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/KnowledgeRetrieveTool.java
test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/controller/MapAiAgentController.java
test -f srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java

grep -q "knowledge_retrieve\|knowledge.retrieve" srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java
grep -q "/api/ai/knowledge" srmp-agent/src/main/java/com/smartroad/srmp/agent/knowledge/controller/AiKnowledgeController.java

echo "==> 检查 Phase36 前端接入"
grep -q "mapAgentChat" srmp-web-ui/src/api/agent.ts
grep -q "ingestKnowledgeMarkdown" srmp-web-ui/src/api/agent.ts
grep -q "searchAiKnowledge" srmp-web-ui/src/api/agent.ts
grep -q "Agent工具" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "参考资料" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "buildMapAiContext" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue

echo "[OK] Phase36 一张图 AI Agent 与向量知识库增强代码检查通过"
