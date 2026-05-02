# Phase39.3：Outline 自动同步调度与 Webhook

## 目标

将手动 Outline 同步升级为自动化链路：

```text
Outline 文档更新
  -> 定时自动同步 / Webhook 精准触发
  -> 写入 ai_knowledge_document / ai_knowledge_chunk
  -> 自动补向量
  -> RAG 检索使用最新内容
```

## 新增能力

```text
1. outline_auto_sync_config 自动同步配置表
2. outline_auto_sync_run 自动同步运行日志表
3. OutlineAutoSyncScheduler 定时扫描到期配置
4. Webhook 接口：POST /api/outline/auto-sync/webhook
5. 自动同步后可自动调用 AiKnowledgeReindexService 补向量
6. 前端页面：/agent/outline-auto-sync
```

## 后端接口

```text
GET  /api/outline/auto-sync/configs
POST /api/outline/auto-sync/configs
PUT  /api/outline/auto-sync/configs/{id}
GET  /api/outline/auto-sync/configs/{id}
POST /api/outline/auto-sync/configs/{id}/run
GET  /api/outline/auto-sync/runs
POST /api/outline/auto-sync/webhook
POST /api/outline/auto-sync/scan-due
```

## Webhook 请求

Header：

```text
X-Outline-Webhook-Secret: <配置中的 webhookSecret>
```

Body 示例：

```json
{
  "event": "document.updated",
  "documentId": "outline-document-id",
  "collectionId": "outline-collection-id"
}
```

`document.deleted` / `document.archived` 会把本地 `ai_knowledge_document` 标记为 `INACTIVE`。

## 应用

```bash
unzip srmp-phase39-3-outline-auto-sync-webhook.zip -d /tmp/phase39-3
cp -r /tmp/phase39-3/srmp-phase39-3-outline-auto-sync-webhook/* /path/to/srmp/

cd /path/to/srmp

psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase39_3_outline_auto_sync_webhook.sql

chmod +x scripts/apply-phase39-3-outline-auto-sync-webhook.sh
chmod +x scripts/check-phase39-3-outline-auto-sync-structure.sh
chmod +x scripts/check-phase39-3-outline-auto-sync-webhook.sh

bash scripts/apply-phase39-3-outline-auto-sync-webhook.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 验收

```bash
bash scripts/check-phase39-3-outline-auto-sync-structure.sh
```

```bash
BASE_URL=http://localhost:8080 SECRET=phase39-3-secret \
bash scripts/check-phase39-3-outline-auto-sync-webhook.sh
```

前端访问：

```text
/agent/outline-auto-sync
```

## 下一步

Phase39.4 建议做：

```text
Outline 同步失败通知与知识库版本对比
```
