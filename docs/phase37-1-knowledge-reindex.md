# Phase37.1：知识库 Reindex / Re-embedding 能力

## 背景

如果知识导入时使用的是 mock embedding，后续测试时才切换真实 embedding，那么 query embedding 与 chunk embedding 不在同一个向量空间中，RAG 质量评测结果会失真。

本阶段提供：

```text
1. ai_knowledge_chunk embedding 元数据字段；
2. POST /api/ai/knowledge/reindex；
3. stats 接口展示 chunkEmbeddingProviders；
4. 前端 /agent/knowledge-vector 支持补齐空向量 / 强制重建向量；
5. reindex 与一致性检查脚本。
```

## 数据库迁移

```bash
psql -d your_db -f srmp-admin/src/main/resources/db/phase37_1_knowledge_reindex.sql
```

新增字段：

```text
embedding_provider
embedding_dimensions
embedded_at
```

## 后端接口

```http
POST /api/ai/knowledge/reindex
```

请求：

```json
{
  "tenantId": "default",
  "sourceType": "MANUAL",
  "force": true,
  "limit": 1000
}
```

返回：

```json
{
  "total": 100,
  "success": 100,
  "failed": 0,
  "embeddingProvider": "local",
  "embeddingModel": "bge-m3",
  "embeddingDimensions": 1024
}
```

## 脚本

强制重建：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default FORCE=true \
bash scripts/reindex-phase37-knowledge-embedding.sh
```

只补齐空向量：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default FORCE=false \
bash scripts/reindex-phase37-knowledge-embedding.sh
```

一致性检查：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/check-phase37-embedding-consistency.sh
```

如仍允许 mock 联调：

```bash
ALLOW_MOCK=true bash scripts/check-phase37-embedding-consistency.sh
```

## 前端

页面：

```text
/agent/knowledge-vector
```

新增按钮：

```text
补齐空向量
强制重建向量
```

新增展示：

```text
Chunk Embedding 来源分布
```

## 推荐操作顺序

```text
1. 配置真实 embedding provider；
2. 重启后端；
3. 执行 phase37_1 migration；
4. 执行强制 reindex；
5. 执行 embedding 一致性检查；
6. 执行 Phase36 E2E；
7. 执行 Phase37 RAG 质量评测。
```
