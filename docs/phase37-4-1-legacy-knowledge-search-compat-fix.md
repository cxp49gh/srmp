# Phase37.4.1：旧知识库搜索接口兼容 AI 向量知识库

## 问题

调用旧接口：

```http
POST /api/knowledge/search
```

请求：

```json
{"query":"PCI 指标是什么意思？","topK":5}
```

返回为空。

原因是：

```text
/api/knowledge/search 是早期知识库接口，查询 knowledge_document / knowledge_chunk；
Phase36+ 导入和检索的向量知识库在 ai_knowledge_document / ai_knowledge_chunk；
如果数据只导入到了新 AI 知识库，旧接口自然查不到。
```

另外旧接口原来用整句 LIKE：

```sql
content LIKE '%PCI 指标是什么意思？%'
```

这种对自然语言问题不友好。

## 修复

`KnowledgeServiceImpl.search()` 调整为：

```text
1. 优先查旧 knowledge_chunk；
2. 旧表无结果时，自动兜底到 AiKnowledgeRetrieverService；
3. 旧表搜索由整句 LIKE 改为分词 OR 查询；
4. 返回结构仍保持旧接口 KnowledgeSearchResult，不影响前端旧页面。
```

## 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-4-1-legacy-knowledge-search.sh
```

或者：

```bash
curl -X POST http://localhost:8080/api/knowledge/search \
  -H "Content-Type: application/json" \
  -d '{"query":"PCI 指标是什么意思？","topK":5}' | python3 -m json.tool
```

预期返回非空，并能看到 `MQI/PQI/PCI` 或 `指标` 相关知识。
