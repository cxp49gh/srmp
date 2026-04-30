# 阶段三十六补强：知识导入与向量检索验收闭环

## 1. 背景

Phase36 已完成并合并到 `main` 后，系统已经具备一张图 AI Agent 与向量知识库增强的基础能力，包括：

```text
一张图 AI Agent
MapAiContext 地图上下文
AI 工具层
KnowledgeRetrieveTool
知识入库接口
知识检索接口
AI Trace
前端 Agent 工具入口
```

但当前还有一个关键问题：

```text
接口部署通过，并不代表向量检索链路已经真正发挥作用。
```

要验证向量库是否真的生效，必须先导入知识数据，并完成从知识入库、切片、embedding、检索、Agent 调用、前端展示、Trace 记录的完整闭环。

因此下一步建议做：

```text
阶段三十六补强：知识导入与向量检索验收闭环
```

---

## 2. 阶段目标

本阶段目标不是继续扩展新的 Agent 能力，而是让 Phase36 能够被真实验证、演示和运维。

目标链路：

```text
导入道路养护知识
  ↓
知识切片
  ↓
生成 embedding
  ↓
写入 ai_knowledge_chunk
  ↓
向量检索
  ↓
一张图 AI 调用 knowledge.retrieve
  ↓
回答展示参考资料
  ↓
Trace 记录检索过程
  ↓
确认是否真正使用向量库
```

核心目标：

```text
1. 提供可直接导入的道路养护知识库样例；
2. 提供一键导入知识脚本；
3. 提供向量检索端到端验收脚本；
4. 增加知识库统计接口；
5. 增加检索 debug 字段；
6. 前端提供知识库状态、导入和检索验证页面；
7. 明确区分“关键词兜底检索”和“真实向量检索”。
```

---

## 3. 当前问题

当前系统状态可以概括为：

```text
Phase36 代码已完成
Agent 接口已部署
前端 Agent 工具入口可用
但知识库中可能没有数据
或者 embedding 未生成
或者当前仍使用 MockEmbeddingClient
或者检索虽然返回结果，但并不确定是否走了 pgvector
```

因此用户会遇到：

```text
1. /api/agent/map-agent/chat 能调用，但 sources 为空；
2. knowledge.retrieve 工具执行成功，但 count=0；
3. /api/ai/knowledge/search 可用，但无法判断是向量检索还是关键词检索；
4. 前端显示“参考资料”不稳定；
5. 只能通过 SQL 日志判断是否出现 embedding <=>；
6. 演示时没有一批稳定的道路养护知识数据。
```

---

## 4. 本阶段不做什么

本阶段不建议做：

```text
1. LangGraph 接入；
2. 多 Agent 协作；
3. 自动转工单；
4. 复杂权限控制；
5. PDF 复杂解析；
6. 大规模知识库运维；
7. 生产级知识审核流程。
```

这些可以放到后续阶段。

本阶段只做：

```text
知识导入
向量检索验证
Agent RAG 验收
前端可视化验证
```

---

## 5. 演示知识库设计

建议新增目录：

```text
docs/knowledge/road-maintenance/
```

内置一批可演示的道路养护知识文档：

```text
01-沥青路面常见病害处置指南.md
02-修补损坏处置技术指南.md
03-裂缝类病害处置指南.md
04-坑槽类病害处置指南.md
05-车辙与沉陷处置指南.md
06-公路技术状况评定指标说明.md
07-MQI-PQI-PCI指标解释.md
08-低分单元养护建议规则.md
09-框选区域养护分析规则.md
10-养护方案编制规范示例.md
```

这些文档需要覆盖一张图中的典型问题：

```text
修补损坏怎么处理？
裂缝类病害如何处置？
坑槽应该如何修复？
中度病害是否需要优先处理？
低分单元怎么判断成因？
MQI / PQI / PCI 分别代表什么？
框选区域病害集中时怎么制定养护建议？
区域养护建议如何组织？
```

---

## 6. 知识文档内容建议

### 6.1 修补损坏处置技术指南

应包含：

```text
1. 病害定义；
2. 常见表现；
3. 成因判断；
4. 现场复核重点；
5. 表层损坏处置；
6. 基层损坏处置；
7. 优先级判断；
8. 质量检查要点。
```

### 6.2 裂缝类病害处置指南

应包含：

```text
1. 横向裂缝；
2. 纵向裂缝；
3. 网裂；
4. 块裂；
5. 轻中重程度判断；
6. 灌缝、封层、罩面等处置策略。
```

### 6.3 低分单元养护建议规则

应包含：

```text
1. MQI/PQI/PCI 指标解释；
2. 低分原因判断；
3. 病害与指标扣分关系；
4. 低分单元处置策略；
5. 优先级排序规则。
```

### 6.4 框选区域养护分析规则

应包含：

```text
1. 区域内病害密度判断；
2. 低分单元数量判断；
3. 病害集中区识别；
4. 区域处置策略；
5. 分期实施建议。
```

---

## 7. 一键导入脚本

建议新增：

```text
scripts/import-phase36-demo-knowledge.sh
```

### 7.1 功能

```text
1. 遍历 docs/knowledge/road-maintenance/*.md；
2. 读取 Markdown 文件内容；
3. 调用 /api/ai/knowledge/ingest/markdown；
4. 写入 sourceType=MANUAL；
5. sourceId 使用文件名；
6. 输出导入成功数量；
7. 导入后自动调用一次检索接口验证。
```

### 7.2 使用方式

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/import-phase36-demo-knowledge.sh
```

### 7.3 脚本参数

```text
BASE_URL    后端地址，默认 http://localhost:8080
TENANT_ID   租户，默认 default
KNOWLEDGE_DIR 知识目录，默认 docs/knowledge/road-maintenance
SOURCE_TYPE 来源类型，默认 MANUAL
```

---

## 8. 向量检索验收脚本

建议新增：

```text
scripts/check-phase36-vector-rag-e2e.sh
```

### 8.1 检查内容

```text
1. 检查 /api/ai/knowledge/stats；
2. 检查 ai_knowledge_chunk 是否有数据；
3. 检查 embeddedChunkCount 是否大于 0；
4. 调用 /api/ai/knowledge/search；
5. 验证 hits 不为空；
6. 验证 searchMode；
7. 调用 /api/agent/map-agent/chat；
8. 验证 toolResults 包含 knowledge.retrieve；
9. 验证 sources 不为空；
10. 验证 trace 中包含 knowledge_retrieve。
```

### 8.2 使用方式

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/check-phase36-vector-rag-e2e.sh
```

### 8.3 验收通过标准

```text
/api/ai/knowledge/search 返回 hits
/api/agent/map-agent/chat 返回 sources
toolResults 包含 knowledge.retrieve
searchMode = VECTOR 或 HYBRID
vectorUsed = true
```

---

## 9. 后端增强建议

### 9.1 新增知识库统计接口

建议新增：

```http
GET /api/ai/knowledge/stats
```

返回示例：

```json
{
  "code": 0,
  "data": {
    "documentCount": 10,
    "chunkCount": 128,
    "embeddedChunkCount": 128,
    "sourceTypes": {
      "MANUAL": 80,
      "OUTLINE": 30,
      "TEMPLATE": 18
    },
    "embeddingProvider": "mock",
    "embeddingModel": "mock-hash-embedding",
    "embeddingDimensions": 1536,
    "vectorEnabled": true
  }
}
```

### 9.2 用途

这个接口用于回答：

```text
当前有没有知识数据？
有多少 chunk？
有多少 chunk 已生成 embedding？
当前使用什么 embedding provider？
向量能力是否启用？
```

---

## 10. 检索接口增强

建议增强：

```http
POST /api/ai/knowledge/search
```

返回中增加 debug 字段：

```json
{
  "query": "修补损坏怎么处理",
  "searchMode": "VECTOR",
  "vectorUsed": true,
  "embeddingProvider": "mock",
  "embeddingDimensions": 1536,
  "fallback": false,
  "fallbackReason": null,
  "hits": []
}
```

如果没有走向量：

```json
{
  "query": "修补损坏怎么处理",
  "searchMode": "KEYWORD_FALLBACK",
  "vectorUsed": false,
  "embeddingProvider": "mock",
  "fallback": true,
  "fallbackReason": "embedding empty or pgvector unavailable",
  "hits": []
}
```

### 10.1 searchMode 建议枚举

```text
VECTOR
KEYWORD
HYBRID
KEYWORD_FALLBACK
NO_DATA
```

### 10.2 vectorUsed

```text
true  表示 SQL 使用了 embedding <=> 或等价向量相似度检索
false 表示没有使用向量相似度
```

---

## 11. Agent 返回增强

建议 `/api/agent/map-agent/chat` 返回：

```json
{
  "answer": "...",
  "sources": [
    {
      "title": "修补损坏处置技术指南",
      "sectionTitle": "处置建议",
      "score": 0.87,
      "sourceType": "MANUAL"
    }
  ],
  "toolResults": [
    {
      "toolName": "knowledge.retrieve",
      "success": true,
      "count": 3,
      "summary": "命中 3 条知识片段"
    }
  ],
  "trace": {
    "steps": [
      {
        "name": "knowledge_retrieve",
        "status": "SUCCESS",
        "data": {
          "searchMode": "VECTOR",
          "vectorUsed": true,
          "hitCount": 3
        }
      }
    ]
  }
}
```

---

## 12. Trace 增强

Phase36 已有 AI Trace，本补强阶段建议在知识检索步骤中记录：

```text
query
topK
sourceType filter
searchMode
vectorUsed
embeddingProvider
hitCount
topScore
fallback
fallbackReason
```

Trace step 示例：

```json
{
  "name": "knowledge_retrieve",
  "label": "知识库检索",
  "status": "SUCCESS",
  "costMs": 126,
  "count": 3,
  "data": {
    "query": "修补损坏怎么处理",
    "searchMode": "VECTOR",
    "vectorUsed": true,
    "embeddingProvider": "mock",
    "hitCount": 3,
    "topScore": 0.87
  }
}
```

---

## 13. 前端增强建议

建议新增页面：

```text
/agent/knowledge-vector
```

页面名称：

```text
向量知识库验证
```

### 13.1 页面功能

```text
1. 知识库统计；
2. Markdown 知识导入；
3. 知识检索验证；
4. 一张图 Agent 验证；
5. 展示 searchMode / vectorUsed / embeddingProvider；
6. 展示 hits、score、sourceType；
7. 展示 toolResults 和 sources。
```

### 13.2 统计卡片

展示：

```text
文档数
切片数
已生成 embedding 数
sourceType 分布
embedding provider
embedding dimensions
vectorEnabled
```

### 13.3 Markdown 导入区

字段：

```text
标题
sourceType
sourceId
Markdown 内容
导入按钮
```

### 13.4 检索验证区

字段：

```text
query
topK
sourceType
检索按钮
```

结果展示：

```text
title
sectionTitle
score
content preview
sourceType
searchMode
vectorUsed
```

### 13.5 Agent 验证区

内置测试问题：

```text
这个修补损坏病害怎么处理？
```

点击后调用：

```http
POST /api/agent/map-agent/chat
```

展示：

```text
answer
sources
toolResults
trace
```

---

## 14. 菜单建议

如果当前前端已有 Agent 菜单，可新增：

```text
AI 能力
  ├── 方案生成
  ├── 方案任务
  ├── 方案模板
  └── 向量知识库验证
```

路由：

```text
/agent/knowledge-vector
```

---

## 15. 数据库验证 SQL

### 15.1 检查 pgvector

```sql
SELECT extname FROM pg_extension WHERE extname = 'vector';
```

### 15.2 检查文档数

```sql
SELECT COUNT(*) FROM ai_knowledge_document;
```

### 15.3 检查 chunk 数

```sql
SELECT COUNT(*) FROM ai_knowledge_chunk;
```

### 15.4 检查 embedding 数

```sql
SELECT COUNT(*) 
FROM ai_knowledge_chunk 
WHERE embedding IS NOT NULL;
```

### 15.5 检查维度

```sql
SELECT vector_dims(embedding)
FROM ai_knowledge_chunk
WHERE embedding IS NOT NULL
LIMIT 5;
```

### 15.6 检查最近导入

```sql
SELECT 
  title,
  section_title,
  source_type,
  chunk_index,
  embedding IS NOT NULL AS has_embedding,
  created_at
FROM ai_knowledge_chunk
ORDER BY created_at DESC
LIMIT 20;
```

---

## 16. 日志验证

打开日志：

```yaml
logging:
  level:
    org.springframework.jdbc.core.JdbcTemplate: DEBUG
    org.springframework.jdbc.core.StatementCreatorUtils: TRACE
    com.smartroad.srmp.agent.knowledge: DEBUG
    com.smartroad.srmp.agent.tool.impl.KnowledgeRetrieveTool: DEBUG
```

查看：

```bash
grep -R "ai_knowledge_chunk" logs/
grep -R "knowledge.retrieve" logs/
grep -R "<=>" logs/
```

如果看到：

```text
embedding <=>
```

说明 SQL 层使用了 pgvector 相似度查询。

---

## 17. 推荐实施顺序

```text
1. 新增演示知识库 docs/knowledge/road-maintenance；
2. 新增 import-phase36-demo-knowledge.sh；
3. 新增 check-phase36-vector-rag-e2e.sh；
4. 后端增加 /api/ai/knowledge/stats；
5. search 接口增加 searchMode / vectorUsed / fallback 字段；
6. Agent Trace 增加知识检索 debug 数据；
7. 前端增加 /agent/knowledge-vector；
8. 导入知识；
9. 执行端到端验收；
10. 再考虑真实 EmbeddingProvider。
```

---

## 18. 验收标准

### 18.1 数据验收

```text
ai_knowledge_document.count > 0
ai_knowledge_chunk.count > 0
embeddedChunkCount > 0
```

### 18.2 检索验收

```text
/api/ai/knowledge/search 返回 hits
searchMode = VECTOR 或 HYBRID
vectorUsed = true
```

### 18.3 Agent 验收

```text
/api/agent/map-agent/chat 返回 answer
toolResults 包含 knowledge.retrieve
sources 不为空
trace 包含 knowledge_retrieve
```

### 18.4 前端验收

```text
/agent/knowledge-vector 显示知识统计
可以导入 Markdown
可以检索知识
可以看到 score
可以看到 Agent sources
可以看到 toolResults
```

---

## 19. 当前阶段判断标准

如果只满足：

```text
接口 200
toolResults 有 knowledge.retrieve
sources 有结果
```

只能说明：

```text
知识检索链路跑通
```

如果进一步满足：

```text
embedding 不为空
searchMode = VECTOR
vectorUsed = true
SQL 出现 embedding <=>
provider 不是 mock
```

才能说明：

```text
真实向量检索生效
```

如果 provider 还是 mock，应描述为：

```text
向量检索链路已打通，真实 embedding 模型待接入
```

---

## 20. 后续阶段建议

完成本补强阶段后，再进入：

### Phase36.2：真实 EmbeddingProvider 接入

```text
OpenAIEmbeddingClient
LocalEmbeddingClient
BGE / bge-m3
Ollama embedding
批量 embedding
失败重试
embedding 任务状态
```

### Phase37：Agent 效果评测

```text
问题集
标准答案
检索命中率
回答质量评分
RAG 回归测试
Prompt 版本管理
```

### Phase38：LangGraph Orchestrator 试点

```text
Python ai-orchestrator
LangGraph workflow
Java 调用 Orchestrator
工具调用状态持久化
Human-in-the-loop
```

---

## 21. 结论

下一步建议不是继续盲目扩展 Agent，而是先做：

```text
阶段三十六补强：知识导入与向量检索验收闭环
```

这样可以把当前 Phase36 从：

```text
代码功能完成
```

推进到：

```text
有知识数据
可检索
可验证
可解释
可演示
```

这是继续做真实 EmbeddingProvider、Agent 评测、LangGraph 的前置基础。
