# Phase39.2：Outline 同步控制台、失败重试与过期清理

## 1. 背景

上一阶段已经定位到：

```text
/api/outline/sync 返回 success，但 fail_count > 0
```

这是状态语义和诊断能力不足的问题。修复后，后端需要进一步让用户能直接看到：

```text
哪篇 Outline 文档失败
为什么失败
是跳过、更新、创建还是重建
失败后如何重试
```

所以本阶段补齐 **同步明细 + 前端控制台 + 失败重试**。

---

## 2. 本次实现内容

### 2.1 数据库

新增：

```text
outline_sync_task_detail
```

记录每篇文档：

```text
outline_document_id
outline_title
collection_id
action
status
skip_reason
error_type
error_message
knowledge_document_id
chunk_count
cost_ms
```

并给 `outline_sync_task` 增加：

```text
dry_run
cleanup_missing
```

### 2.2 后端接口

新增：

```text
GET  /api/outline/sync-tasks/{id}/details
POST /api/outline/sync-tasks/{id}/retry-failed
```

增强：

```text
POST /api/outline/sync
```

支持：

```text
dryRun
cleanupMissing
documentIds
retryTaskId
```

### 2.3 同步逻辑增强

```text
1. 分页同步，不再只同步第一页；
2. 每篇文档都有 detail；
3. fail_count > 0 不会再返回 SUCCESS；
4. dryRun 不写入 ai_knowledge_*，只预演 CREATE/UPDATE/SKIP；
5. retry-failed 只重试上次失败文档；
6. cleanupMissing 可将本地已不可见的 Outline 文档标记为 INACTIVE。
```

### 2.4 前端 OutlineSyncPage 增强

新增能力：

```text
1. Dry Run 开关；
2. cleanupMissing 开关；
3. 点击任务查看明细；
4. 按 FAILED/SUCCESS/SKIPPED 过滤；
5. 显示失败 error_type / error_message；
6. 一键重试失败文档。
```

---

## 3. 应用方式

```bash
unzip srmp-phase39-2-outline-sync-console-retry.zip -d /tmp/phase39-2
cp -r /tmp/phase39-2/srmp-phase39-2-outline-sync-console-retry/* /path/to/srmp/

cd /path/to/srmp

psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase39_2_outline_sync_console_retry.sql

chmod +x scripts/check-phase39-2-outline-sync-console-retry.sh
chmod +x scripts/check-phase39-2-outline-no-legacy-write.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

---

## 4. 验收

### 4.1 后端 dryRun + 正式同步

```bash
BASE_URL=http://localhost:8080 LIMIT=500 FORCE=false \
bash scripts/check-phase39-2-outline-sync-console-retry.sh
```

预期：

```text
1. dryRun 返回 DRY_RUN / DRY_RUN_PARTIAL；
2. dryRun detail 不会出现 SUCCESS；
3. 正式同步 fail_count > 0 时状态不会是 SUCCESS；
4. detail 可看到每篇文档失败原因。
```

### 4.2 检查不再写旧知识库表

```bash
bash scripts/check-phase39-2-outline-no-legacy-write.sh
```

预期：

```text
Outline 同步链路只走 AiKnowledgeIngestService -> ai_knowledge_document / ai_knowledge_chunk。
```

### 4.3 前端验证

访问：

```text
/agent/outline-sync
```

验证：

```text
1. 能看到 Dry Run / 清理过期开关；
2. 点击同步任务能打开明细抽屉；
3. 失败任务可以点击“重试失败”；
4. 明细中能看到具体 error_type / error_message。
```

---

## 5. 下一步建议

Phase39.2 通过后，再做：

```text
Phase39.3：Outline 自动同步调度与 Webhook
```

重点：

```text
1. 定时同步配置；
2. 手动开关；
3. Outline webhook；
4. 最近同步状态仪表盘；
5. 同步失败通知。
```
