#!/usr/bin/env bash
set -euo pipefail

grep -q "mapAgentChat" srmp-web-ui/src/api/agent.ts
grep -q "ingestKnowledgeMarkdown" srmp-web-ui/src/api/agent.ts
grep -q "searchAiKnowledge" srmp-web-ui/src/api/agent.ts
grep -q "Agent工具" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "参考资料" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
grep -q "buildMapAiContext" srmp-web-ui/src/views/gis/components/AgentChatFloat.vue

echo "[OK] Phase36 前端代码已实现"
