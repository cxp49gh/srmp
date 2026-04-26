# 阶段十七：Outline 同步入库说明

## 1. 目标

将 Outline 在线文档同步到本地知识库表：

```text
Outline 文档 → knowledge_document → knowledge_chunk → AI RAG 问答
```

## 2. 新增接口

```text
GET  /api/outline/collections
POST /api/outline/documents/list
POST /api/outline/sync
GET  /api/outline/sync-tasks
GET  /api/outline/sync-tasks/{id}
```

## 3. 初始化 SQL

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase17_outline_sync.sql
```

如果还没执行阶段十三 SQL，先执行：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
```

## 4. 前端页面

```text
http://localhost:5173/agent/outline-sync
```

## 5. 验证

同步后检查：

```sql
SELECT source_type, count(*) FROM knowledge_document WHERE tenant_id='default' GROUP BY source_type;
SELECT source_type, count(*) FROM knowledge_chunk WHERE tenant_id='default' GROUP BY source_type;
```

然后访问：

```text
http://localhost:5173/agent/knowledge-search
http://localhost:5173/agent/chat
```