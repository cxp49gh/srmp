# 阶段三十六：一张图 AI Agent 与向量知识库增强实现说明

## 1. 实现范围

本阶段已完成第一版最小闭环：

```text
知识入库
  ↓
向量检索 / 关键词兜底
  ↓
一张图 AI Agent 调用 knowledge.retrieve
  ↓
结合地图上下文生成回答
  ↓
前端展示参考资料与工具调用
  ↓
Trace 展示工具调用过程
```

## 2. 后端新增能力

### 2.1 数据库

新增迁移脚本：

```text
srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql
```

新增表：

```text
ai_knowledge_document
ai_knowledge_chunk
```

默认使用 `pgvector`：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

如果部署环境暂不支持 pgvector，Java 检索服务会在向量检索失败时自动降级为关键词检索。

### 2.2 Embedding

新增包：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/embedding
```

包含：

```text
EmbeddingClient
EmbeddingProperties
MockEmbeddingClient
```

当前第一版默认使用 `MockEmbeddingClient`，基于文本 token hash 生成稳定向量，便于本地演示和无外部 embedding 服务时使用。

配置示例：

```yaml
srmp:
  ai:
    embedding:
      provider: mock
      model: mock-hash-embedding
      dimensions: 1536
```

### 2.3 知识入库与检索

新增接口：

```http
POST /api/ai/knowledge/ingest/markdown
POST /api/ai/knowledge/search
```

导入 Markdown 示例：

```json
{
  "title": "沥青路面病害处置指南",
  "sourceType": "MANUAL",
  "sourceId": "manual-001",
  "content": "# 修补损坏\n中度修补损坏建议先复核基层，再进行局部铣刨重铺。"
}
```

检索示例：

```json
{
  "query": "修补损坏中度病害怎么处理",
  "topK": 5,
  "sourceTypes": ["MANUAL", "OUTLINE", "TEMPLATE"]
}
```

### 2.4 AI 工具层

新增包：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/tool
```

核心接口：

```text
AiTool
AiToolContext
AiToolResult
AiToolRegistry
```

第一版内置工具：

```text
gis.queryDiseases
gis.queryAssessmentResults
gis.queryRegionSummary
gis.queryNearbyObjects
knowledge.retrieve
template.match
solution.generateDraft
solution.saveTask
```

### 2.5 一张图 AI Agent

新增接口：

```http
POST /api/agent/map-agent/chat
```

核心服务：

```text
MapAiAgentService
MapAiAgentServiceImpl
```

处理流程：

```text
map_context_build
agent_intent_recognize
tool_plan
tool_execute
prompt_build
llm_answer
answer_sanitize
```

## 3. 前端新增能力

### 3.1 API

`srmp-web-ui/src/api/agent.ts` 新增：

```text
mapAgentChat
ingestKnowledgeMarkdown
searchAiKnowledge
```

### 3.2 一张图 AI 面板

`AgentChatFloat.vue` 增强：

```text
1. 新增“Agent工具”开关；
2. 默认使用 /api/agent/map-agent/chat；
3. 自动构建 MapAiContext；
4. AI 回答展示参考资料；
5. AI 回答展示工具调用摘要；
6. Trace 继续复用 AiTraceDrawer。
```

## 4. 验收脚本

新增：

```bash
scripts/check-phase36-map-ai-agent-knowledge.sh
```

执行：

```bash
bash scripts/check-phase36-map-ai-agent-knowledge.sh
```

## 5. 构建建议

```bash
mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 6. 使用建议

1. 执行数据库迁移脚本；
2. 导入一篇 Markdown 知识；
3. 打开 `/gis/one-map`；
4. 点击病害对象；
5. 勾选“Agent工具”；
6. 提问“这个病害怎么处理”；
7. 查看回答中的参考资料和 Trace。
