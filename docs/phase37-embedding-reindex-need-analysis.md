# 关于“测试时才配置 Embedding，但导入时未配置”的影响与处理建议

## 1. 核心结论

如果知识导入时没有配置真实 Embedding，而是在后续测试时才配置真实 Embedding，那么当前评测结果很可能不具备真实参考价值。

因为向量 RAG 链路里有两次 Embedding：

```text
1. 入库时：
   文档 chunk → embedding → 写入 ai_knowledge_chunk.embedding

2. 检索时：
   query → embedding → 与 ai_knowledge_chunk.embedding 做相似度检索
```

如果导入时使用的是默认 MockEmbeddingClient，或者 embedding 为空，而测试时才切换成真实 Embedding，那么会出现：

```text
query embedding 使用真实模型生成
chunk embedding 却是 mock 生成或旧模型生成
```

这会导致：

```text
两边不在同一个向量空间
  ↓
相似度计算失真
  ↓
检索命中不稳定
  ↓
RAG 质量评测分数偏低
```

因此，当前 `RAG 质量评测通过率 33.3%` 很可能不是模型回答能力问题，而是：

```text
知识入库阶段的 embedding 与检索阶段的 embedding 不一致
```

---

## 2. 为什么改配置不会自动重算历史 Embedding

Embedding 是在知识入库或重建索引时写入数据库的。

例如：

```text
导入知识时：
  provider = mock

写入数据库：
  ai_knowledge_chunk.embedding = mock 向量
```

后面即使修改配置：

```yaml
srmp:
  ai:
    embedding:
      provider: local
      model: bge-m3
```

数据库中已有的 `ai_knowledge_chunk.embedding` 也不会自动变化。

也就是说：

```text
修改 Embedding 配置
  ≠
自动重算已有知识向量
```

必须执行：

```text
重新导入知识
或
重新生成 embedding / reindex
```

---

## 3. 当前可能出现的状态

你当前系统可能是以下几种状态之一。

### 3.1 知识导入时未生成 Embedding

表现：

```sql
SELECT COUNT(*) 
FROM ai_knowledge_chunk 
WHERE embedding IS NOT NULL;
```

结果为：

```text
0
```

说明知识切片存在，但没有向量。

此时向量检索无法真正生效，只能走关键词兜底或无命中。

---

### 3.2 知识导入时使用 MockEmbeddingClient

表现：

```text
ai_knowledge_chunk.embedding 不为空
```

但导入时系统配置是：

```yaml
provider: mock
```

这说明向量字段有值，但这些向量只是 mock 向量。

这种状态下可以验证：

```text
链路是否跑通
接口是否正常
toolResults 是否返回
Trace 是否记录
```

但不能用于判断真实语义检索效果。

---

### 3.3 知识导入时使用旧模型，测试时使用新模型

例如：

```text
导入时：text-embedding-3-small，1536 维
测试时：bge-m3，1024 维
```

或者：

```text
导入时：mock
测试时：bge-m3
```

这会导致：

```text
query embedding 和 chunk embedding 不一致
```

轻则检索质量很差，重则维度不匹配导致 SQL 查询失败。

---

## 4. 先确认当前真实状态

### 4.1 查看知识库统计接口

执行：

```bash
curl http://localhost:8080/api/ai/knowledge/stats?tenantId=default | python3 -m json.tool
```

重点查看：

```text
embeddingProvider
embeddingModel
embeddingDimensions
documentCount
chunkCount
embeddedChunkCount
vectorEnabled
```

如果看到：

```text
embeddingProvider = mock
```

说明当前仍然是 mock embedding。

如果看到：

```text
embeddedChunkCount = 0
```

说明知识还没有生成 embedding。

---

### 4.2 查看数据库中的 chunk 和 embedding

```sql
SELECT COUNT(*) AS chunk_count
FROM ai_knowledge_chunk;
```

```sql
SELECT COUNT(*) AS embedded_count
FROM ai_knowledge_chunk
WHERE embedding IS NOT NULL;
```

```sql
SELECT vector_dims(embedding)
FROM ai_knowledge_chunk
WHERE embedding IS NOT NULL
LIMIT 5;
```

如果 `vector_dims` 返回：

```text
1536
```

说明当前向量维度是 1536。

如果返回：

```text
1024
```

说明当前向量维度是 1024。

该维度必须与当前配置的 Embedding 模型一致。

---

## 5. 正确处理流程

### 5.1 第一步：确认真实 Embedding Provider 已生效

例如本地 Embedding：

```yaml
srmp:
  ai:
    embedding:
      provider: local
      endpoint: http://localhost:8002/embed
      model: bge-m3
      dimensions: 1024
      batch-size: 16
```

或 OpenAI 兼容接口：

```yaml
srmp:
  ai:
    embedding:
      provider: openai-compatible
      endpoint: https://api.openai.com
      api-key: ${OPENAI_API_KEY}
      model: text-embedding-3-small
      dimensions: 1536
      batch-size: 16
```

修改配置后，必须重启后端服务。

重启后执行：

```bash
curl http://localhost:8080/api/ai/knowledge/stats?tenantId=default | python3 -m json.tool
```

确认：

```text
embeddingProvider 不是 mock
embeddingDimensions 与模型一致
```

---

### 5.2 第二步：清理旧的演示知识

如果当前导入的是 Phase36 演示知识，可以先删除旧数据。

谨慎版 SQL：

```sql
WITH target_docs AS (
    SELECT id
    FROM ai_knowledge_document
    WHERE tenant_id = 'default'
      AND source_type = 'MANUAL'
      AND (
        source_id LIKE '%.md'
        OR source_id LIKE 'manual-%'
        OR title LIKE '%处置指南%'
        OR title LIKE '%指标%'
        OR title LIKE '%养护%'
      )
)
DELETE FROM ai_knowledge_chunk
WHERE document_id IN (SELECT id FROM target_docs);
```

然后删除文档：

```sql
DELETE FROM ai_knowledge_document
WHERE tenant_id = 'default'
  AND source_type = 'MANUAL'
  AND (
    source_id LIKE '%.md'
    OR source_id LIKE 'manual-%'
    OR title LIKE '%处置指南%'
    OR title LIKE '%指标%'
    OR title LIKE '%养护%'
  );
```

如果不想直接删除，也可以后续做 reindex 功能，对旧数据重新生成 embedding。

---

### 5.3 第三步：重新导入知识

确认真实 Embedding Provider 已生效后，重新导入演示知识：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/import-phase36-demo-knowledge.sh
```

导入后再次检查：

```sql
SELECT COUNT(*) FROM ai_knowledge_document;
```

```sql
SELECT COUNT(*) FROM ai_knowledge_chunk;
```

```sql
SELECT COUNT(*) 
FROM ai_knowledge_chunk 
WHERE embedding IS NOT NULL;
```

```sql
SELECT vector_dims(embedding)
FROM ai_knowledge_chunk
WHERE embedding IS NOT NULL
LIMIT 5;
```

---

### 5.4 第四步：重新跑 Phase36 端到端验收

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/check-phase36-vector-rag-e2e.sh
```

重点确认：

```text
searchMode = VECTOR 或 HYBRID
vectorUsed = true
sources 不为空
toolResults 包含 knowledge.retrieve
trace 包含 knowledge_retrieve
```

---

### 5.5 第五步：重新跑 Phase37 RAG 质量评测

```bash
BASE_URL=http://localhost:8080 \
bash scripts/eval-phase37-rag-quality.sh
```

这时评测结果才更有参考价值。

---

## 6. 为什么 33.3% 可能是合理现象

如果知识是用 mock embedding 导入的，而测试时切换了真实 embedding，那么：

```text
query embedding = 真实语义向量
chunk embedding = mock 伪向量
```

这种情况下：

```text
检索结果可能随机
expectedSources 不容易命中
answer 依据不稳定
关键词覆盖不足
```

因此评测通过率低是正常的。

更准确的阶段判断应该是：

```text
Phase36 RAG 链路已打通
但真实语义检索质量需要在重新生成 embedding 后评估
```

---

## 7. 建议补充 Reindex / Re-embedding 能力

当前问题说明系统还缺一个重要能力：

```text
当 Embedding Provider 变化后，对已有知识重新生成 embedding
```

建议新增：

```text
Phase37.1：知识库 Reindex / Re-embedding 能力
```

---

## 8. Reindex 接口设计

### 8.1 接口

```http
POST /api/ai/knowledge/reindex
```

### 8.2 请求示例

```json
{
  "tenantId": "default",
  "sourceType": "MANUAL",
  "documentIds": [],
  "force": true
}
```

### 8.3 参数说明

| 字段 | 说明 |
|---|---|
| `tenantId` | 租户 |
| `sourceType` | 来源类型，可选 |
| `documentIds` | 指定文档 ID，可为空 |
| `force` | 是否强制重算已有 embedding |

### 8.4 处理逻辑

```text
1. 查询符合条件的 ai_knowledge_chunk；
2. 如果 force=false，只处理 embedding 为空的 chunk；
3. 如果 force=true，所有符合条件的 chunk 都重新生成 embedding；
4. 使用当前 EmbeddingClient 生成 embedding；
5. 更新 ai_knowledge_chunk.embedding；
6. 写入 embedding_provider / embedding_model / embedding_dimensions / embedded_at；
7. 返回成功数、失败数、耗时。
```

---

## 9. 建议扩展 ai_knowledge_chunk 字段

为了以后能知道每个 chunk 是用哪个模型生成的，建议增加：

```sql
ALTER TABLE ai_knowledge_chunk
ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(64),
ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128),
ADD COLUMN IF NOT EXISTS embedding_dimensions INTEGER,
ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMP;
```

这样可以区分：

```text
当前系统配置使用 bge-m3
但某些历史 chunk 可能还是 mock 生成
```

否则只能知道当前 provider，无法判断历史向量来源。

---

## 10. 知识库统计接口也应增强

`/api/ai/knowledge/stats` 建议返回：

```json
{
  "documentCount": 10,
  "chunkCount": 128,
  "embeddedChunkCount": 128,
  "embeddingProvider": "local",
  "embeddingModel": "bge-m3",
  "embeddingDimensions": 1024,
  "vectorEnabled": true,
  "chunkEmbeddingProviders": {
    "mock": 50,
    "local:bge-m3": 78
  }
}
```

这样可以一眼看出：

```text
是否存在混合 embedding provider 的历史数据
```

---

## 11. 推荐下一步

建议下一步不是继续调 Prompt，而是先做：

```text
Phase37.1：知识库 Reindex / Re-embedding 能力
```

交付内容：

```text
1. ai_knowledge_chunk 增加 embedding 元数据字段；
2. 新增 /api/ai/knowledge/reindex；
3. 新增 reindex service；
4. 新增 reindex 脚本；
5. stats 接口展示 chunk embedding provider 分布；
6. 前端 /agent/knowledge-vector 页面增加“重建向量”按钮；
7. 重新导入或 reindex 后再跑 RAG 质量评测。
```

---

## 12. 推荐操作顺序

当前建议按以下顺序处理：

```text
1. 确认当前 embeddingProvider 是否为真实模型；
2. 如果仍是 mock，先配置真实 provider；
3. 重启后端；
4. 清理旧知识或执行 reindex；
5. 重新导入知识；
6. 确认 embedding 维度与真实模型一致；
7. 跑 Phase36 E2E；
8. 跑 Phase37 RAG 评测；
9. 再根据失败用例调 Prompt、topK、评测规则。
```

---

## 13. 结论

你的判断是正确的：

```text
导入时没有配置真实 embedding，测试时才配置 embedding
```

这会导致：

```text
已有知识 chunk 的 embedding 不是当前真实模型生成
```

因此当前 33.3% 的评测结果很可能不代表真实 RAG 效果。

正确做法是：

```text
配置真实 Embedding Provider
  ↓
重新导入知识或执行 Reindex
  ↓
确保 chunk embedding 与 query embedding 使用同一模型
  ↓
再进行 RAG 质量评测
```

否则，向量相似度检索的结果会受到旧向量或 mock 向量影响，评测通过率自然偏低。
