# Phase36 前端接入修复包

这个包专门修复检查脚本提示的前端缺失：

```text
srmp-web-ui/src/api/agent.ts
- mapAgentChat
- ingestKnowledgeMarkdown
- searchAiKnowledge

srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
- Agent工具
- 参考资料
- buildMapAiContext
```

## 使用方式

```bash
unzip srmp-phase36-frontend-apply-fix.zip -d /tmp/phase36-fe
cp -r /tmp/phase36-fe/srmp-phase36-frontend-apply-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase36-frontend-agent.sh scripts/check-phase36-frontend-agent.sh
./scripts/apply-phase36-frontend-agent.sh
./scripts/check-phase36-frontend-agent.sh
npm --prefix srmp-web-ui run build
```

脚本会先备份：

```text
agent.ts.phase36.bak
AgentChatFloat.vue.phase36.bak
```
