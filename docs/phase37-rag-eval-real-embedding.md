# 阶段三十七：AI Agent 评测、质量回归与真实 Embedding 接入

## 1. 背景

Phase36 已完成一张图 AI Agent 与向量知识库 RAG 端到端验收，下一步要解决：

```text
1. 检索准不准？
2. 回答稳不稳？
3. 当前是否仍是 MockEmbeddingClient？
4. 参数变更后效果是否可回归？
```

因此 Phase37 建设目标是：

```text
真实 EmbeddingProvider + RAG 评测集 + 自动评测脚本 + 前端评测页面 + 质量回归报告
```

## 2. 本次实现

### 后端

```text
1. MockEmbeddingClient 改为 provider=mock 时生效；
2. 新增 LocalEmbeddingClient，支持本地 embedding 服务；
3. 新增 OpenAiCompatibleEmbeddingClient，支持 OpenAI 兼容 embedding 接口；
4. 新增 /api/ai/eval/rag/default-cases；
5. 新增 /api/ai/eval/rag/run；
6. 新增 RagEvalService，对 answer、sources、toolResults、vectorUsed 做评测。
```

### 前端

```text
1. 新增 /agent/rag-eval 页面；
2. 支持加载默认评测用例；
3. 支持执行评测；
4. 展示通过率、关键词命中、来源数、searchMode、vectorUsed、错误原因。
```

### 脚本与数据

```text
1. docs/eval/phase37-rag/road-maintenance-rag-cases.json；
2. scripts/eval-phase37-rag-quality.sh。
```

## 3. Embedding 配置

### Mock 默认配置

```yaml
srmp:
  ai:
    embedding:
      provider: mock
      model: mock-hash-embedding
      dimensions: 1536
```

### 本地 embedding 服务

```yaml
srmp:
  ai:
    embedding:
      provider: local
      endpoint: http://localhost:8002/embed
      model: bge-m3
      dimensions: 1024
```

本地接口兼容以下响应结构之一：

```json
{"embeddings": [[0.1, 0.2]]}
```

或：

```json
{"data": [{"embedding": [0.1, 0.2]}]}
```

### OpenAI 兼容接口

```yaml
srmp:
  ai:
    embedding:
      provider: openai-compatible
      endpoint: https://api.openai.com
      api-key: ${OPENAI_API_KEY}
      model: text-embedding-3-small
      dimensions: 1536
```

如果 endpoint 不以 `/embeddings` 结尾，会自动拼接 `/v1/embeddings`。

## 4. 使用方式

```bash
mvn clean package -DskipTests
npm --prefix srmp-web-ui run build

BASE_URL=http://localhost:8080 \
bash scripts/eval-phase37-rag-quality.sh
```

前端页面：

```text
/agent/rag-eval
```

## 5. 验收标准

```text
1. /api/ai/eval/rag/default-cases 返回用例；
2. /api/ai/eval/rag/run 返回 passRate；
3. 至少部分用例 passed=true；
4. 每个用例可查看 sourceCount、keywordMatchedCount、searchMode、vectorUsed；
5. 切换 provider=local/openai-compatible 后，stats 中 embeddingProvider 变化。
```

## 6. 后续建议

```text
Phase37.1：增加更多评测问题；
Phase37.2：RAG 参数自动对比 topK/scoreThreshold/sourceType；
Phase37.3：引入 LLM Judge；
Phase38：LangGraph Orchestrator 试点。
```
