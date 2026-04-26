# 阶段十八：AI 知识库最终收口与验收说明

## 1. 阶段目标

阶段十八用于对一期 AI 知识库增强能力进行最终收口，重点不是新增业务功能，而是让整条链路可验证、可复现、可交付。

当前链路：

```text
本地知识库文档
  ↓
knowledge_document
  ↓
knowledge_chunk
  ↓
/api/knowledge/search
  ↓
/api/knowledge/ask
  ↓
/api/agent/chat

Outline
  ↓
/api/outline/status
  ↓
/api/outline/search
  ↓
/api/outline/sync
  ↓
knowledge_document / knowledge_chunk
  ↓
AI RAG 问答
```

## 2. 新增文件

```text
scripts/check-ai-knowledge.sh
scripts/check-outline.sh
scripts/check-ai-e2e.sh
srmp-admin/src/main/resources/db/phase18_ai_demo_knowledge.sql
docs/phase18-ai-knowledge-acceptance.md
docs/ai-knowledge-demo-guide.md
```

## 3. 初始化演示知识库

先执行阶段十三 SQL：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
```

再执行阶段十八演示知识库：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase18_ai_demo_knowledge.sql
```

如果要验证 Outline 同步，还需要执行阶段十七 SQL：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase17_outline_sync.sql
```

## 4. 自动验收

### 4.1 验收本地知识库

```bash
chmod +x scripts/*.sh
./scripts/check-ai-knowledge.sh
```

覆盖：

```text
1. /api/knowledge/search
2. /api/knowledge/ask
3. /api/agent/chat useKnowledge=true
```

### 4.2 验收 Outline

```bash
./scripts/check-outline.sh
```

覆盖：

```text
1. /api/outline/status
2. /api/outline/collections
3. /api/outline/documents/list
4. /api/outline/sync
5. /api/outline/sync-tasks
```

如果 Outline 未启用，脚本会给出提示并跳过后续检查。

### 4.3 端到端验收

```bash
./scripts/check-ai-e2e.sh
```

## 5. 环境变量

```bash
export BASE_URL=http://localhost:8080
export TENANT_ID=default
```

Outline 验证需要：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你的_Outline_API_Token
```

## 6. 验收标准

| 验收项 | 标准 |
|---|---|
| 知识库搜索 | `/api/knowledge/search` 返回非空列表 |
| 知识库问答 | `/api/knowledge/ask` 返回非空 answer |
| 混合问答 | `/api/agent/chat` 返回非空 answer |
| Outline 状态 | `/api/outline/status` 返回 code=0 |
| Outline 同步 | `/api/outline/sync` 返回任务结果 |
| 同步任务 | `/api/outline/sync-tasks` 返回任务列表 |
| 前端页面 | AI 问答、知识库检索、Outline 页面可访问 |

## 7. 建议封版状态

阶段十八完成后，一期 AI 增强可以认为具备：

```text
1. 本地知识库录入；
2. 本地知识库检索；
3. 本地知识库问答；
4. AI 问答融合业务数据和知识库；
5. Outline 在线搜索；
6. Outline 本地 Docker 联调；
7. Outline 同步入库；
8. 自动验收脚本。
```
