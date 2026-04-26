# 阶段十三：一期 AI 知识库增强与 Outline 在线搜索使用说明

## 1. 新增能力

```text
1. 本地知识库文档表 knowledge_document
2. 本地知识库切片表 knowledge_chunk
3. Markdown/TXT 文本切片入库
4. /api/knowledge/documents 文档录入
5. /api/knowledge/search 知识库检索
6. /api/knowledge/ask 知识库问答
7. Outline 在线搜索配置
8. /api/outline/status
9. /api/outline/search
10. /api/outline/documents/{id}
11. /api/agent/chat 融合业务数据 + 知识库 + Outline
```

## 2. 初始化 SQL

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
```

## 3. Outline 配置

在 `application-demo.yml` 或环境变量中增加：

```yaml
srmp:
  outline:
    enabled: ${OUTLINE_ENABLED:false}
    base-url: ${OUTLINE_BASE_URL:}
    api-token: ${OUTLINE_API_TOKEN:}
    sync-enabled: false
    default-collection-id: ${OUTLINE_DEFAULT_COLLECTION_ID:}
    search-limit: ${OUTLINE_SEARCH_LIMIT:5}
```

环境变量示例：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=https://outline.example.com
export OUTLINE_API_TOKEN=your-outline-token
```

## 4. 知识库文档录入

```http
POST http://localhost:8080/api/knowledge/documents
Content-Type: application/json
X-Tenant-Id: default

{
  "title": "病害复核流程",
  "category": "MAINTENANCE_FLOW",
  "docType": "MARKDOWN",
  "sourceType": "LOCAL",
  "content": "# 病害复核流程\n病害导入后应先进行人工复核，确认位置、类型和严重程度..."
}
```

## 5. 知识库搜索

```http
POST http://localhost:8080/api/knowledge/search
Content-Type: application/json
X-Tenant-Id: default

{
  "query": "PCI 指标是什么意思",
  "topK": 5
}
```

## 6. 知识库问答

```http
POST http://localhost:8080/api/knowledge/ask
Content-Type: application/json
X-Tenant-Id: default

{
  "query": "数据导入模板怎么使用？",
  "topK": 5
}
```

## 7. Outline 状态

```http
GET http://localhost:8080/api/outline/status
X-Tenant-Id: default
```

## 8. Outline 搜索

```http
POST http://localhost:8080/api/outline/search
Content-Type: application/json
X-Tenant-Id: default

{
  "query": "病害复核流程",
  "limit": 5
}
```

## 9. AI 混合问答

```http
POST http://localhost:8080/api/agent/chat
Content-Type: application/json
X-Tenant-Id: default

{
  "message": "根据知识库解释 PCI 指标，并结合 G210 2026 年情况给出建议",
  "context": {
    "routeCode": "G210",
    "year": 2026,
    "indexCode": "PCI"
  },
  "options": {
    "useBusinessData": true,
    "useKnowledge": true,
    "useOutline": false,
    "topK": 5
  }
}
```

返回中会包含：

```text
data.knowledgeSources
data.outlineSources
data.usedKnowledge
data.usedOutline
```

## 10. 说明

本阶段先实现关键词 RAG，不接 pgvector，不做 Outline 全量同步。后续可继续扩展：

```text
1. Outline Collection 同步
2. 文档 hash 增量更新
3. pgvector 向量检索
4. 混合检索 rerank
5. 前端知识库管理页面
```
