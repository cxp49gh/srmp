# 如何确认 Phase36 是否真的使用了向量库

## 1. 核心结论

部署测试通过，并不等于一定使用了向量库。

要确认 Phase36 的“一张图 AI Agent 与向量知识库增强”是否真的走了向量检索，需要从以下几个层面验证：

```text
1. pgvector 扩展是否安装；
2. ai_knowledge_chunk 是否有知识切片；
3. embedding 字段是否有向量数据；
4. 知识检索接口是否能返回命中结果；
5. 一张图 AI 是否调用了 knowledge.retrieve 工具；
6. 前端是否展示“参考资料”和“工具调用”；
7. 后端 SQL 是否出现 pgvector 相似度查询；
8. 当前 embedding provider 是否是真实模型，而不是 mock。
```

最关键的判断标准是：

```text
数据库中 embedding 不为空
  +
SQL 日志出现 embedding <=> 查询
  +
Agent 返回 toolResults 中包含 knowledge.retrieve
```

满足这些条件，才能基本确认向量检索链路已经生效。

---

## 2. 验证 pgvector 是否安装

进入 PostgreSQL 执行：

```sql
SELECT extname 
FROM pg_extension 
WHERE extname = 'vector';
```

如果返回：

```text
vector
```

说明 pgvector 扩展已经安装。

如果没有结果，需要先执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

> 注意：如果数据库环境没有安装 pgvector 扩展包，仅执行 `CREATE EXTENSION` 可能会失败，需要先在数据库服务器安装 pgvector。

---

## 3. 验证知识切片是否入库

查询知识切片表：

```sql
SELECT 
  id,
  title,
  section_title,
  source_type,
  chunk_index,
  embedding IS NOT NULL AS has_embedding,
  created_at
FROM ai_knowledge_chunk
ORDER BY created_at DESC
LIMIT 10;
```

重点看：

```text
has_embedding = true
```

如果都是 false，说明知识入库可能只保存了文本，没有生成 embedding。

---

## 4. 验证 embedding 维度

执行：

```sql
SELECT vector_dims(embedding)
FROM ai_knowledge_chunk
WHERE embedding IS NOT NULL
LIMIT 5;
```

正常应返回配置的 embedding 维度，例如：

```text
1536
```

或：

```text
1024
```

具体取决于当前 embedding 模型。

如果报错，可能有几种情况：

```text
1. pgvector 扩展未安装；
2. embedding 字段不是 vector 类型；
3. embedding 字段为空；
4. 当前 PostgreSQL / pgvector 版本不支持 vector_dims。
```

---

## 5. 验证知识入库接口

可以先导入一条测试知识：

```bash
curl -X POST http://localhost:8080/api/ai/knowledge/ingest/markdown \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "default",
    "title": "沥青路面病害处置指南",
    "sourceType": "MANUAL",
    "sourceId": "manual-test-001",
    "content": "# 修补损坏\n修补损坏通常需要现场复核修补边界、基层状态和排水条件。若为表层损坏，可采用局部铣刨、清理、重新摊铺或热补修复。"
  }'
```

导入后检查：

```sql
SELECT 
  title,
  source_type,
  chunk_index,
  content,
  embedding IS NOT NULL AS has_embedding
FROM ai_knowledge_chunk
WHERE source_id = 'manual-test-001'
ORDER BY chunk_index;
```

预期：

```text
能查到切片
has_embedding = true
```

---

## 6. 验证知识检索接口

调用知识检索接口：

```bash
curl -X POST http://localhost:8080/api/ai/knowledge/search \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "default",
    "query": "修补损坏怎么处理",
    "topK": 5,
    "filters": {
      "sourceType": ["MANUAL"]
    }
  }'
```

正常返回应该包含类似：

```json
{
  "code": 0,
  "data": {
    "query": "修补损坏怎么处理",
    "hits": [
      {
        "title": "沥青路面病害处置指南",
        "sectionTitle": "修补损坏",
        "score": 0.86,
        "content": "修补损坏通常需要现场复核修补边界...",
        "sourceType": "MANUAL"
      }
    ]
  }
}
```

如果能返回 `hits`，说明知识检索接口跑通。

但注意：

```text
知识检索接口跑通 ≠ 一定用了向量检索
```

还需要继续看 SQL 是否使用了 pgvector 的相似度算子。

---

## 7. 验证一张图 AI 是否调用知识检索工具

调用一张图 Agent 接口：

```bash
curl -X POST http://localhost:8080/api/agent/map-agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "这个修补损坏病害怎么处理？",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "DISEASE",
        "routeCode": "G210",
        "startStake": 69.007,
        "endStake": 69.034,
        "diseaseName": "修补损坏",
        "severity": "MEDIUM"
      }
    },
    "options": {
      "useKnowledge": true,
      "useBusinessData": true,
      "useTools": true,
      "topK": 5
    }
  }'
```

重点看返回里是否包含：

```json
{
  "toolResults": [
    {
      "toolName": "knowledge.retrieve",
      "success": true,
      "count": 1
    }
  ],
  "sources": [
    {
      "title": "沥青路面病害处置指南",
      "score": 0.86
    }
  ]
}
```

如果 `toolResults` 里有：

```text
knowledge.retrieve
```

并且 `sources` 不为空，说明一张图 AI 已经调用知识检索工具。

---

## 8. 前端验证

打开 `/gis/one-map`，点击地图上的病害或评定对象，打开 AI 面板。

确认：

```text
1. “Agent工具”已勾选；
2. 输入：这个病害怎么处理？
3. 回答下方显示“参考资料”；
4. 回答下方显示“工具调用”；
5. 工具调用中包含 knowledge.retrieve；
6. 参考资料中显示知识标题、章节和 score。
```

如果前端没有显示“参考资料”或“工具调用”，但后端返回里有 `sources` / `toolResults`，说明是前端展示问题。

如果后端返回里也没有，则说明 Agent 没有调用知识检索工具。

---

## 9. 最关键：看 SQL 是否使用 pgvector 相似度算子

真正能证明“走了向量库”的，是 SQL 中出现 pgvector 相似度查询。

典型 SQL：

```sql
SELECT ...
FROM ai_knowledge_chunk
WHERE tenant_id = ?
ORDER BY embedding <=> ?::vector
LIMIT ?
```

或：

```sql
ORDER BY embedding <=> CAST(:embedding AS vector)
```

其中：

```text
<=> 是 pgvector 的 cosine distance 相似度算子
```

如果只看到：

```sql
WHERE content LIKE ?
```

或：

```sql
WHERE title LIKE ?
```

那说明当前走的是关键词检索或兜底检索，不是向量相似度检索。

---

## 10. 打开 SQL 日志

如果当前日志里看不到 SQL，可以临时打开 Spring JDBC 日志。

在配置文件中增加：

```yaml
logging:
  level:
    org.springframework.jdbc.core.JdbcTemplate: DEBUG
    org.springframework.jdbc.core.StatementCreatorUtils: TRACE
    com.smartroad.srmp.agent.knowledge: DEBUG
    com.smartroad.srmp.agent.tool.impl.KnowledgeRetrieveTool: DEBUG
```

然后重新调用：

```bash
curl -X POST http://localhost:8080/api/agent/map-agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "修补损坏怎么处理？",
    "mapContext": {
      "mode": "OBJECT",
      "routeCode": "G210",
      "year": 2026,
      "mapObject": {
        "objectType": "DISEASE",
        "diseaseName": "修补损坏",
        "severity": "MEDIUM"
      }
    },
    "options": {
      "useKnowledge": true,
      "useTools": true,
      "topK": 5
    }
  }'
```

查看日志：

```bash
grep -R "ai_knowledge_chunk" logs/
grep -R "knowledge.retrieve" logs/
grep -R "<=>" logs/
```

如果看到：

```text
embedding <=>
```

说明真正走了 pgvector 向量检索。

---

## 11. 检查是否仍是 MockEmbeddingClient

执行：

```bash
grep -R "embedding" -n srmp-*/src/main/resources
grep -R "MockEmbeddingClient" -n srmp-agent/src/main/java
grep -R "OpenAIEmbeddingClient" -n srmp-agent/src/main/java
grep -R "LocalEmbeddingClient" -n srmp-agent/src/main/java
```

如果当前只有：

```text
MockEmbeddingClient
```

或者配置是：

```yaml
srmp:
  ai:
    embedding:
      provider: mock
```

说明当前只是模拟 embedding，用于打通链路。

这种情况下可以说：

```text
知识库检索链路已打通，向量接口已预留，但真实 embedding 模型尚未接入。
```

还不能说：

```text
真实语义向量检索已经上线。
```

---

## 12. MockEmbeddingClient 与真实向量检索的区别

### 12.1 MockEmbeddingClient

特点：

```text
1. 不调用真实 embedding 模型；
2. 一般用固定算法生成伪向量；
3. 适合开发、联调、接口验证；
4. 检索结果质量不代表真实语义效果。
```

### 12.2 真实 EmbeddingClient

可以是：

```text
OpenAIEmbeddingClient
LocalEmbeddingClient
BGE / bge-m3
Ollama embedding
其他本地模型服务
```

真实向量检索应满足：

```text
1. 入库时调用真实 embedding 模型；
2. 查询时对 query 生成 embedding；
3. SQL 使用 embedding <=> queryEmbedding；
4. 检索结果按相似度排序；
5. score 能反映语义相关性。
```

---

## 13. 推荐验收标准

### 13.1 知识库检索链路生效

满足以下条件即可认为知识检索链路生效：

```text
1. ai_knowledge_chunk 有数据；
2. /api/ai/knowledge/search 能返回 hits；
3. /api/agent/map-agent/chat 返回 toolResults；
4. toolResults 中包含 knowledge.retrieve；
5. 前端显示“参考资料”。
```

### 13.2 真实向量库检索生效

必须进一步满足：

```text
1. pgvector 扩展已安装；
2. ai_knowledge_chunk.embedding 不为空；
3. vector_dims(embedding) 返回正确维度；
4. 后端 SQL 日志出现 embedding <=>；
5. 当前 embedding provider 不是 mock；
6. query 会生成真实 embedding；
7. 检索结果按向量相似度排序。
```

---

## 14. 快速检查 SQL 汇总

### 14.1 检查 pgvector 扩展

```sql
SELECT extname FROM pg_extension WHERE extname = 'vector';
```

### 14.2 检查知识切片总数

```sql
SELECT COUNT(*) FROM ai_knowledge_chunk;
```

### 14.3 检查 embedding 数量

```sql
SELECT COUNT(*) 
FROM ai_knowledge_chunk 
WHERE embedding IS NOT NULL;
```

### 14.4 检查维度

```sql
SELECT vector_dims(embedding)
FROM ai_knowledge_chunk
WHERE embedding IS NOT NULL
LIMIT 5;
```

### 14.5 检查最近入库

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

## 15. 快速判断表

| 现象 | 说明 |
|---|---|
| `/api/agent/map-agent/chat` 返回 200 | Agent 接口可用 |
| `toolResults` 有 `knowledge.retrieve` | Agent 调用了知识检索工具 |
| `sources` 不为空 | 知识库命中内容已返回 |
| 前端显示“参考资料” | 前端展示链路生效 |
| `ai_knowledge_chunk.embedding` 不为空 | 入库时生成了向量 |
| SQL 有 `embedding <=>` | 使用了 pgvector 相似度检索 |
| provider 是 `mock` | 只是模拟向量，不是真实 embedding |
| provider 是 OpenAI / local bge 等 | 真实 embedding 模型参与 |

---

## 16. 结论

要判断是否真正使用向量库，不能只看接口是否正常，也不能只看前端是否显示参考资料。

最可靠的判断链路是：

```text
1. ai_knowledge_chunk 中 embedding 不为空；
2. 查询时后端生成 query embedding；
3. SQL 使用 embedding <=> 进行相似度排序；
4. /api/agent/map-agent/chat 的 toolResults 包含 knowledge.retrieve；
5. 返回 sources 并在前端显示参考资料；
6. embedding provider 不是 mock。
```

满足这些条件后，才能确认：

```text
Phase36 一张图 AI Agent 已经真正接入并使用向量知识库。
```
