# Phase37.4：RAG 检索增强与一张图 AI 依据面板

## 1. 阶段背景

前面阶段已经完成：

```text
Phase36：一张图 AI Agent + 向量知识库 RAG 主链路
Phase37：真实 Embedding + RAG 质量评测
Phase37.1：知识库 Reindex / Re-embedding
Phase37.2：评测规则增强
Phase37.3：AI 健康检查与产品化收口
```

当前 AI 能力已经可检查、可评测、可维护。下一步重点是：

```text
1. 让短问题也能稳定检索到正确知识；
2. 让普通业务用户看懂 AI 回答依据；
3. 让 RAG 检索从单一向量检索升级为 query rewrite + hybrid retrieval；
4. 让 Trace 和前端都能展示 rewrittenQuery / retrievalStrategy / vectorUsed / fallback。
```

---

## 2. 本阶段目标

将当前：

```text
用户问题 -> 向量检索 -> 回答
```

升级为：

```text
用户问题 + 地图上下文
  ↓
Query Rewrite
  ↓
Vector Search + Keyword Search
  ↓
Hybrid Merge
  ↓
可解释回答 + 依据面板
```

---

## 3. 后端改造

### 3.1 RagProperties

新增配置：

```yaml
srmp:
  ai:
    rag:
      top-k: 8
      score-threshold: 0.0
      max-context-chars: 6000
      hybrid:
        enabled: true
        vector-top-k: 10
        keyword-top-k: 10
        vector-weight: 0.7
        keyword-weight: 0.3
```

### 3.2 Query Rewrite

新增：

```text
RagQueryRewriteService
RagQueryRewriteServiceImpl
```

用于将短问题改写成适合检索的 query。

例如：

```text
输入：分析当前对象
地图对象：G210 / 中度 / 修补损坏
输出：分析当前对象 G210 中度 修补损坏 病害 成因判断 处置建议 基层 排水 局部铣刨 修补边界
```

### 3.3 Hybrid Search

`AiKnowledgeRetrieverServiceImpl` 增强：

```text
1. 向量检索 vectorSearch；
2. 关键词检索 keywordSearch；
3. 按 chunkId 合并去重；
4. vectorScore * vectorWeight + keywordScore * keywordWeight；
5. 返回 topK；
6. response 标识：
   - retrievalStrategy = HYBRID
   - searchMode = HYBRID
   - vectorUsed = true
   - originalQuery
   - rewrittenQuery
```

如果向量失败：

```text
searchMode = KEYWORD_FALLBACK
vectorUsed = false
fallback = true
fallbackReason = ...
```

---

## 4. 前端改造

### 4.1 一张图 AI 依据面板

新增组件：

```text
srmp-web-ui/src/views/gis/components/AiEvidencePanel.vue
```

在 AI 回答下方展示：

```text
当前地图上下文
知识来源 sources
工具调用 toolResults
searchMode
retrievalStrategy
vectorUsed
fallback
topScore
rewrittenQuery
```

这样用户不用打开 Trace，也能知道 AI 回答依据。

---

## 5. 验收

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/check-phase37-4-rag-hybrid-e2e.sh
```

预期：

```text
1. /api/ai/knowledge/search 返回 searchMode=HYBRID 或 VECTOR；
2. vectorUsed=true；
3. rewrittenQuery 不为空；
4. retrievalStrategy 不为空；
5. /api/agent/map-agent/chat 返回 toolResults；
6. knowledge.retrieve 的 data 中包含 rewrittenQuery / retrievalStrategy；
7. 前端 AI 回答下方显示“回答依据”。
```
