# Outline 文档同步整理实施文档

> 本文档供代码生成模型执行实现使用。不要把本文当产品宣讲稿；执行时必须按阶段落地，每一阶段只改对应范围，完成验收后再进入下一阶段。

## 0. 执行原则

1. 先做入口和职责整理，再做同步闭环增强，最后做自动化、监控和运营治理。
2. 不要一次性重写现有 Outline 模块。当前系统已经有可用的后端接口、前端页面和数据表，优先复用。
3. 不要破坏旧路由。所有旧地址必须保留并重定向到新地址。
4. 普通用户只看只读入口；业务管理员能同步、看任务、补向量；系统管理员才能维护自动同步、Webhook Secret、高风险操作。
5. 所有写操作必须走租户隔离，不能绕过 `TenantContextHolder` 和已有 `tenant_id` 约束。
6. 页面展示必须明确区分：
   - Outline 在线搜索：只查 Outline，不入库。
   - 本地知识库检索：查 `ai_knowledge_document` / `ai_knowledge_chunk`，可用于 RAG。
   - 同步成功：只代表文档入库，不一定代表已向量化。
   - RAG 可用：文档已入库且 chunk 已完成 embedding。

## 1. 当前代码基线

### 1.1 前端

前端工程：`srmp-web-ui`

已有文件：

| 文件 | 当前作用 |
| --- | --- |
| `srmp-web-ui/src/router/index.ts` | 当前路由集中定义，已有 `/agent/outline-status`、`/agent/outline-search`、`/agent/outline-sync`、`/agent/outline-auto-sync` |
| `srmp-web-ui/src/views/agent/components/AgentPageShell.vue` | 侧边导航硬编码在这里 |
| `srmp-web-ui/src/api/outline.ts` | Outline 前端 API 封装 |
| `srmp-web-ui/src/views/agent/OutlineStatusPage.vue` | 连接状态页 |
| `srmp-web-ui/src/views/agent/OutlineSearchPage.vue` | Outline 在线搜索页 |
| `srmp-web-ui/src/views/agent/OutlineSyncPage.vue` | 手动同步、任务明细、补向量、检索验证 |
| `srmp-web-ui/src/views/agent/OutlineAutoSyncPage.vue` | 自动同步、Webhook、运行记录 |
| `srmp-web-ui/src/views/agent/AiChatPage.vue` | AI 问答，已有 Outline 来源展示 |
| `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue` | GIS 浮窗问答，已有 Outline 选项 |

### 1.2 后端

后端模块：`srmp-agent`

已有文件：

| 文件 | 当前作用 |
| --- | --- |
| `OutlineController` | `/api/outline/status`、在线搜索、文档详情 |
| `OutlineSyncController` | Collection、文档列表、同步、补向量、知识库统计、任务、明细、失败重试 |
| `OutlineAutoSyncController` | 自动同步配置、立即运行、运行记录、Webhook、到期扫描 |
| `OutlineService` / `OutlineServiceImpl` | Outline 在线能力 |
| `OutlineSyncService` / `OutlineSyncServiceImpl` | 同步入库、任务明细、本地知识库状态挂载 |
| `OutlineAutoSyncService` / `OutlineAutoSyncServiceImpl` | 自动同步和 Webhook |
| `OutlineAutoSyncScheduler` | 到期配置扫描调度 |
| `AiKnowledgeReindexService` | 向量补齐和强制重建 |

### 1.3 现有接口

不要重复新增同义接口，优先复用：

| 能力 | 接口 |
| --- | --- |
| 连接状态 | `GET /api/outline/status` |
| 在线搜索 | `POST /api/outline/search` |
| 文档详情 | `GET /api/outline/documents/{id}` |
| Collection 列表 | `GET /api/outline/collections` |
| 文档列表 | `POST /api/outline/documents/list` |
| 手动同步 | `POST /api/outline/sync` |
| 知识库统计 | `GET /api/outline/knowledge-stats` |
| OUTLINE 补向量 | `POST /api/outline/vectorize` |
| 同步任务列表 | `GET /api/outline/sync-tasks` |
| 同步任务详情 | `GET /api/outline/sync-tasks/{id}` |
| 同步明细 | `GET /api/outline/sync-tasks/{id}/details` |
| 失败重试 | `POST /api/outline/sync-tasks/{id}/retry-failed` |
| 自动同步配置 | `GET/POST/PUT /api/outline/auto-sync/configs` |
| 立即运行 | `POST /api/outline/auto-sync/configs/{id}/run` |
| 运行记录 | `GET /api/outline/auto-sync/runs` |
| Webhook | `POST /api/outline/auto-sync/webhook` |
| 到期扫描 | `POST /api/outline/auto-sync/scan-due` |

## 2. 总体落地顺序

必须按下面顺序实施：

| 阶段 | 优先级 | 目标 | 主要文件 |
| --- | --- | --- | --- |
| 阶段 1 | P0 | 菜单与新旧路由整理 | `router/index.ts`、`AgentPageShell.vue` |
| 阶段 2 | P0 | 连接状态和文档搜索说明补强 | `OutlineStatusPage.vue`、`OutlineSearchPage.vue` |
| 阶段 3 | P0 | 手动同步闭环补齐 | `OutlineSyncPage.vue`、`outline.ts`、必要时 `OutlineSyncServiceImpl` |
| 阶段 4 | P0 | 向量化和 RAG 可用性判断 | `OutlineSyncPage.vue`、`OutlineDocumentDTO`、`OutlineSyncServiceImpl` |
| 阶段 5 | P1 | 同步任务独立页面 | 新建 `OutlineTasksPage.vue`，复用任务接口 |
| 阶段 6 | P1 | 自动同步与 Webhook 安全优化 | `OutlineAutoSyncPage.vue`、`OutlineAutoSyncServiceImpl` |
| 阶段 7 | P1 | 运行监控独立页面 | 新建 `OutlineRunsPage.vue`，复用 runs/configs/stats |
| 阶段 8 | P2 | AI 来源展示和反馈闭环 | `AiChatPage.vue`、`AgentChatFloat.vue`、相关后端反馈表和接口 |
| 阶段 9 | P2 | 内容治理看板 | 新建治理页和统计接口 |

阶段 1 到阶段 4 是最小可交付范围。阶段 5 到阶段 7 是管理效率增强。阶段 8 到阶段 9 是后续运营治理。

## 3. 阶段 1：菜单与入口整理

### 3.1 目标

把散落在一级导航中的 Outline 入口收敛到 `Outline 文档同步` 分组下，并引入新路由。

### 3.2 路由要求

在 `srmp-web-ui/src/router/index.ts` 中新增新路由：

| 页面 | 新路由 | 旧路由兼容 |
| --- | --- | --- |
| 连接状态 | `/agent/outline/status` | `/agent/outline-status` |
| 文档搜索 | `/agent/outline/search` | `/agent/outline-search` |
| 同步入库 | `/agent/outline/sync` | `/agent/outline-sync` |
| 同步任务 | `/agent/outline/tasks` | 暂无旧独立路由 |
| 自动同步 | `/agent/outline/auto-sync` | `/agent/outline-auto-sync` |
| 运行监控 | `/agent/outline/runs` | 暂无旧独立路由 |

旧路由不要继续直接挂页面，改为 `redirect` 到新路由。

示例方式：

```ts
{
  path: '/agent/outline-search',
  redirect: '/agent/outline/search'
}
```

### 3.3 导航要求

修改 `AgentPageShell.vue`：

1. 一级导航中不要再平铺：
   - `Outline 搜索`
   - `Outline 同步`
   - `Outline 自动同步`
   - `Outline 状态`
2. 新增一个视觉分组 `Outline 文档同步`。
3. 子项顺序必须是：
   - 连接状态
   - 文档搜索
   - 同步入库
   - 同步任务
   - 自动同步
   - 运行监控

如果当前项目还没有真实权限系统接入前端导航，先实现本地静态分组，不要硬造复杂权限框架。权限可用 `meta.permission` 预留。

### 3.4 验收

- 访问 `/agent/outline/search` 能打开搜索页。
- 访问旧地址 `/agent/outline-search` 会跳到 `/agent/outline/search`。
- 侧边栏只出现一个 Outline 分组。
- 父菜单不承载复杂业务页。

## 4. 阶段 2：连接状态与文档搜索整理

### 4.1 连接状态页

文件：`OutlineStatusPage.vue`

必须展示：

- `enabled`
- `usable`
- `baseUrl`
- `syncEnabled`
- `defaultCollectionId`
- `message`
- 如后端已有 `diagnostics`，展示诊断字段，但不要让页面崩溃。

不可用提示规则：

| 条件 | 提示 |
| --- | --- |
| `enabled=false` | Outline 功能未启用，请联系管理员开启 |
| `usable=false` | Outline 配置不完整或不可访问，请检查 baseUrl、Token、网络 |
| `syncEnabled=false` | 当前只允许在线搜索，不允许同步入库 |

### 4.2 文档搜索页

文件：`OutlineSearchPage.vue`

必须保留顶部说明：

```text
此为 Outline 实时在线检索，不会写入本地知识库；只有已同步并完成向量化的文档才能稳定参与 AI/RAG。
```

搜索行为要求：

- 空关键词不调用接口。
- 默认 `limit=5` 或 `10`。
- 展示标题、摘要、文档 ID、分数、原文链接。
- “查看详情”调用 `GET /api/outline/documents/{id}`。
- 普通用户不能在搜索页看到“同步”按钮。

### 4.3 验收

- Outline 不可用时，状态页有明确原因。
- 搜索页说明了“在线搜索不等于入库”。
- 搜索结果能打开原文或详情。

## 5. 阶段 3：手动同步入库闭环

### 5.1 页面结构

文件：`OutlineSyncPage.vue`

页面必须分四块：

1. 统计区：
   - OUTLINE 文档数
   - OUTLINE chunk 数
   - 已向量化 chunk 数
   - 待补向量 chunk 数

2. 同步配置区：
   - Collection
   - limit
   - force
   - dryRun
   - cleanupMissing
   - documentIds

3. 文档预览区：
   - 标题
   - 文档 ID
   - 更新时间
   - 原文链接
   - 本地同步状态
   - 知识库文档 ID
   - chunk 数
   - embedding 数
   - RAG 是否可用

4. 任务与明细区：
   - 最近任务列表
   - 任务状态
   - 总数、成功、跳过、失败
   - 明细抽屉
   - 失败重试

### 5.2 同步执行规则

调用 `POST /api/outline/sync` 时传：

```json
{
  "collectionId": "collection-id",
  "limit": 500,
  "force": false,
  "dryRun": false,
  "cleanupMissing": false,
  "documentIds": ["doc-1", "doc-2"]
}
```

规则：

- 勾选了文档时，必须传 `documentIds`。
- 未勾选文档时，按 Collection + limit 同步。
- `dryRun=true` 时按钮文案必须明确为“Dry Run 预演”。
- `force=true`、`cleanupMissing=true` 必须二次确认。
- 同步完成后刷新统计、任务列表、文档列表。

### 5.3 后端注意事项

当前 `OutlineSyncServiceImpl` 已经有：

- `sync`
- `retryFailed`
- `knowledgeStats`
- `documents` 挂载本地知识库状态

不要重写整类。只在缺字段或状态不准确时做小范围补充。

必须保证：

- `dryRun` 不写 `ai_knowledge_document` / `ai_knowledge_chunk`。
- `dryRun` 可以生成 `outline_sync_task` 和明细。
- 单篇失败不影响其他文档。
- 内容未变化写 `SKIPPED`。
- 失败明细记录可读 `errorMessage`。

### 5.4 验收

- 可加载 Collection。
- 可加载文档列表。
- 可选择指定文档同步。
- Dry Run 不写知识库，但生成任务和明细。
- 正式同步写知识库和 chunk。
- 失败文档可重试。

## 6. 阶段 4：向量化状态与 RAG 可用性

### 6.1 状态定义

前后端统一使用以下本地知识库状态：

| 状态 | 含义 |
| --- | --- |
| `NOT_SYNCED` | 未同步到本地知识库 |
| `INACTIVE` | 本地文档不可用 |
| `PENDING_VECTOR` | 已入库但没有完成 embedding |
| `RAG_READY` | 已入库且 chunk 已完成 embedding，可用于 RAG |

### 6.2 前端展示

在文档预览、任务明细、同步结果中展示“可用于 AI”。

判断逻辑：

```ts
ragReady === true || kbSyncStatus === 'RAG_READY'
```

待补向量数大于 0 时必须突出提示，并提供“补向量”按钮。

### 6.3 补向量

调用：

```json
POST /api/outline/vectorize
{
  "force": false,
  "dryRun": false,
  "limit": 500
}
```

规则：

- 默认只补 embedding 为空的 OUTLINE chunk。
- `force=true` 是高风险操作，必须二次确认。
- 补向量完成后刷新统计和文档列表。

### 6.4 检索验证

同步完成后提供验证入口：

- 用文档标题调本地知识库检索。
- 限定 `sourceType=OUTLINE`。
- 验证结果展示命中的文档和 chunk。

不要调用 Outline 在线搜索来验证 RAG，因为那不能证明已入库。

### 6.5 验收

- 页面可见 `pendingEmbeddingChunkCount`。
- pending 大于 0 时有告警和补向量入口。
- 补向量后 pending 下降或归零。
- RAG 可用状态和本地 embedding 状态一致。

## 7. 阶段 5：同步任务独立化

### 7.1 新建页面

新增文件：

```text
srmp-web-ui/src/views/agent/OutlineTasksPage.vue
```

新增路由：

```text
/agent/outline/tasks
```

### 7.2 页面职责

这个页面只做任务审计和排错，不做 Collection 选择，不做自动同步配置。

必须包含：

- 任务列表
- 状态筛选
- 时间筛选，如后端暂不支持，可先前端过滤最近数据
- Dry Run 标记
- 任务摘要
- 任务明细抽屉
- 明细状态筛选：全部、成功、跳过、失败
- 失败重试
- 失败类型统计

### 7.3 API 使用

优先复用：

- `getOutlineSyncTasks`
- `getOutlineSyncTask`
- `getOutlineSyncTaskDetails`
- `retryOutlineFailedTask`

如果需要后端筛选，再扩展 `GET /api/outline/sync-tasks` 参数：

```http
GET /api/outline/sync-tasks?status=&collectionId=&dryRun=&from=&to=&limit=
```

不要为了筛选新建第二套任务接口。

### 7.4 验收

- 不进入同步入库页，也能查看历史任务。
- 明细能打开 Outline 原文。
- 明细能看到知识库文档 ID、chunk 数、hash、错误原因。
- 失败任务能按错误类型聚合。

## 8. 阶段 6：自动同步与 Webhook

### 8.1 页面整理

文件：`OutlineAutoSyncPage.vue`

保留并整理：

- 自动同步配置列表
- 配置表单
- 立即运行
- Webhook 地址
- Header 示例
- curl 示例
- Webhook 测试
- 同步后自动补向量策略

### 8.2 Secret 安全

要求：

- Secret 默认脱敏展示。
- 不要在普通用户或业务管理员只读视图中显示完整 Secret。
- 支持重新生成 Secret。
- 修改 Secret 必须是系统管理员权限。

如果当前项目没有前端权限接入，先做到 UI 上明确“系统管理员操作”，不要扩大给普通用户。

### 8.3 Webhook 事件

后端必须支持：

| 事件 | 行为 |
| --- | --- |
| `document.created` | 同步指定文档 |
| `document.updated` | 同步指定文档 |
| `document.deleted` | 本地 OUTLINE 文档标记不可用或清理 |
| `document.archived` | 本地 OUTLINE 文档标记不可用或清理 |

Secret 支持三种传法：

- `X-Outline-Webhook-Secret`
- `X-Webhook-Secret`
- `Authorization: Bearer <secret>`

### 8.4 验收

- 可新增、编辑、启停自动同步配置。
- 可立即运行。
- Secret 正确时 Webhook 触发同步。
- Secret 错误时拒绝执行，不暴露正确 Secret。
- Webhook 更新事件只同步指定文档。

## 9. 阶段 7：运行监控独立化

### 9.1 新建页面

新增文件：

```text
srmp-web-ui/src/views/agent/OutlineRunsPage.vue
```

新增路由：

```text
/agent/outline/runs
```

### 9.2 页面职责

运行监控只回答“自动同步和 Webhook 是否健康”，不要承担文档同步入口。

必须展示：

- 最近成功时间
- 最近失败时间
- 连续失败次数
- Webhook 触发次数
- Webhook 失败次数
- 待补向量数量
- 运行记录列表
- 按配置、触发方式、状态、时间筛选
- 到期扫描按钮，仅系统管理员可操作

### 9.3 API 使用

复用：

- `getOutlineAutoSyncConfigs`
- `getOutlineAutoSyncRuns`
- `getOutlineKnowledgeStats`
- `scanOutlineAutoSyncDue`

如果后端缺连续失败统计，先前端从最近 runs 计算；后续再补后端字段。

### 9.4 验收

- 能区分 `SCHEDULED`、`MANUAL`、`WEBHOOK`。
- 连续失败配置突出展示。
- 待补向量堆积突出展示。
- 可打开关联的 `syncTaskId` 明细。

## 10. 阶段 8：普通用户 AI 来源闭环

### 10.1 来源展示

涉及页面：

- `AiChatPage.vue`
- `AgentChatFloat.vue`
- `SolutionGeneratePage.vue`
- `SolutionTasksPage.vue`

AI 答案来源必须区分：

- 业务数据
- 本地知识库
- Outline 文档

Outline 来源展示字段：

- 文档标题
- 命中片段
- 更新时间
- 同步时间
- 原文链接
- 是否来自本地知识库

### 10.2 反馈入口

新增两个用户动作：

- 知识缺失反馈
- 答案来源不准确反馈

反馈记录至少包含：

- 用户问题
- 当前业务对象，如路线、病害、评定单元
- 引用来源
- 用户 ID
- 租户 ID
- 创建时间
- 反馈类型
- 反馈备注

### 10.3 验收

- 普通用户能看懂答案引用了哪些文档。
- 用户能打开 Outline 原文。
- 用户能反馈缺失或错误资料。
- 管理员能看到反馈记录。

## 11. 阶段 9：内容治理与运营看板

这是 P2，不要在 P0/P1 阶段提前实现。

建议新增：

- 知识覆盖看板
- 低质量文档列表
- 引用热度统计
- 待治理任务

统计维度：

- 病害类型
- 评定指标
- 处置工艺
- 路面类型
- 方案模板

低质量规则：

- 空文档
- 过短文档
- 默认 Outline 系统文档
- 长期未更新
- 零引用

## 12. 状态和错误类型规范

### 12.1 同步任务状态

| 状态 | 含义 |
| --- | --- |
| `RUNNING` | 执行中 |
| `SUCCESS` | 全部成功或成功加跳过，无失败 |
| `PARTIAL_SUCCESS` | 部分成功，部分失败 |
| `FAILED` | 任务级失败或全部失败 |
| `DRY_RUN` | 预演完成，无失败 |
| `DRY_RUN_PARTIAL` | 预演完成，部分失败 |

### 12.2 明细状态

| 状态 | 含义 |
| --- | --- |
| `SUCCESS` | 单篇文档成功入库 |
| `SKIPPED` | 单篇文档跳过 |
| `FAILED` | 单篇文档失败 |

### 12.3 错误类型

后续扩展明细时，使用这些枚举，不要随便写散乱字符串：

| 错误类型 | 含义 |
| --- | --- |
| `CONFIG_ERROR` | Outline 配置缺失或不可用 |
| `OUTLINE_API_ERROR` | 调 Outline API 失败 |
| `EMPTY_CONTENT` | 文档内容为空 |
| `INGEST_ERROR` | 写入知识库失败 |
| `VECTOR_ERROR` | 向量化失败 |
| `PERMISSION_ERROR` | 权限不足 |
| `TENANT_ERROR` | 租户上下文缺失或不匹配 |
| `UNKNOWN_ERROR` | 未分类异常 |

## 13. 权限建议

如果项目已有后端权限体系，把下面权限点接入菜单和接口；如果暂时没有，先写入 `meta.permission` 并在文档里保留，不要硬编码假鉴权。

| 权限点 | 说明 |
| --- | --- |
| `outline:status:view` | 查看连接状态 |
| `outline:search` | 在线搜索 Outline |
| `outline:document:view` | 查看文档详情 |
| `outline:sync:view` | 查看同步页和任务 |
| `outline:sync:run` | 执行同步 |
| `outline:sync:dry-run` | 执行 Dry Run |
| `outline:sync:retry` | 重试失败文档 |
| `outline:vectorize` | 补向量 |
| `outline:vectorize:force` | 强制重建向量 |
| `outline:auto-sync:view` | 查看自动同步配置和运行记录 |
| `outline:auto-sync:manage` | 新增或修改自动同步配置 |
| `outline:webhook:test` | 测试 Webhook |
| `outline:webhook:secret` | 查看或修改 Secret |

角色默认可见性：

| 角色 | 可见入口 |
| --- | --- |
| 普通业务用户 | 连接状态、文档搜索 |
| 业务管理员 | 连接状态、文档搜索、同步入库、同步任务 |
| 系统管理员 | 全部 |
| 研发/测试 | 连接状态、文档搜索、同步任务、运行监控 |
| 内容运营 | 文档搜索、同步任务只读、运行监控只读 |

## 14. 高风险操作规则

以下操作必须二次确认：

- `force=true` 同步。
- `cleanupMissing=true`。
- `vectorize force=true`。
- Webhook Secret 重置。
- 自动同步配置删除或停用。
- 删除/归档事件清理本地知识库。

确认弹窗必须说明影响范围，例如：

```text
将强制重新同步选中文档，可能重建 chunk 和 embedding。确认继续？
```

## 15. 数据一致性要求

1. `outline_sync_task` 必须记录每次同步。
2. `outline_sync_task_detail` 必须记录每篇文档的同步结果。
3. `ai_knowledge_document.source_type` 必须为 `OUTLINE`。
4. `ai_knowledge_document.source_id` 必须保存 Outline document id。
5. `ai_knowledge_chunk.source_type` 必须为 `OUTLINE`。
6. 所有相关表必须包含并使用当前 `tenant_id`。
7. Webhook 触发后必须能定位到对应租户配置。
8. 文档删除或归档不要直接物理删除，优先标记不可用并保留审计记录。

## 16. 前端实现约束

1. 继续使用 Vue 3 + Element Plus。
2. 继续复用 `AgentPageShell.vue`。
3. 继续复用 `srmp-web-ui/src/api/outline.ts`，不要在页面里直接写裸 `request`。
4. 新页面要和已有 Agent 页面视觉一致。
5. 表格中长 ID、URL、错误信息必须支持 tooltip 或展开，不要撑爆页面。
6. 状态颜色统一：
   - 成功：绿色
   - 执行中：蓝色或黄色
   - 部分成功：橙色
   - 失败：红色
   - 跳过：灰色
   - Dry Run：蓝色
7. 移动端不能让按钮和文字重叠。

## 17. 后端实现约束

1. 不要新增和现有接口重复的 Controller。
2. Controller 继续返回 `R.ok(...)`。
3. Java 代码保持 Java 8 兼容。
4. SQL 必须带 `tenant_id`。
5. 单文档异常必须捕获并写明细，不能让整个任务没有记录。
6. 向量化只处理 `sourceType=OUTLINE`。
7. Webhook Secret 不匹配时不要返回正确 Secret。
8. 后端错误信息要可读，但不能泄露 Token。

## 18. 验证清单

### 18.1 前端构建

在 `srmp-web-ui` 下执行：

```bash
npm run build
```

如果项目依赖未安装，先使用项目现有依赖安装方式，不要改锁文件，除非任务明确要求。

### 18.2 后端编译

在仓库根目录执行：

```bash
mvn -pl srmp-agent -am test
```

如果测试环境缺数据库导致测试无法跑，至少执行：

```bash
mvn -pl srmp-agent -am -DskipTests package
```

### 18.3 手工验收

最小手工验收路径：

1. 打开 `/agent/outline/status`。
2. 打开 `/agent/outline/search`，搜索一个关键词。
3. 打开 `/agent/outline/sync`，加载 Collection 和文档。
4. 执行 Dry Run，检查任务和明细。
5. 执行正式同步，检查知识库统计。
6. 如果 pending embedding 大于 0，执行补向量。
7. 用本地知识库检索验证 OUTLINE 文档可召回。
8. 打开 `/agent/outline/tasks` 查看历史任务。
9. 打开 `/agent/outline/auto-sync` 立即运行一个配置。
10. 打开 `/agent/outline/runs` 查看运行记录。

## 19. 禁止事项

1. 禁止删除旧路由。
2. 禁止把 Outline 在线搜索结果当成本地 RAG 可用结果。
3. 禁止普通用户看到同步、Webhook、强制向量化等写操作。
4. 禁止在前端暴露 Outline API Token。
5. 禁止明文向无权限用户展示 Webhook Secret。
6. 禁止跨租户查询任务、文档、chunk、自动同步配置。
7. 禁止物理删除知识库文档作为默认删除/归档策略。
8. 禁止一次 PR 同时实现 P0、P1、P2 全部内容。

## 20. 推荐提交拆分

建议拆成以下小提交或小 PR：

1. `outline routes and navigation`
   - 新路由、旧路由 redirect、导航分组。

2. `outline status and search copy`
   - 状态页提示、搜索页说明和展示优化。

3. `outline sync usability`
   - 同步页状态、Dry Run、任务明细、失败重试、风险确认。

4. `outline rag readiness`
   - pending embedding 强提示、补向量、RAG 可用状态、检索验证。

5. `outline task page`
   - 独立同步任务页面。

6. `outline auto sync security`
   - Secret 脱敏、Webhook 测试、自动同步配置体验。

7. `outline runs page`
   - 独立运行监控页面。

8. `outline ai source feedback`
   - AI 来源展示和反馈闭环。

## 21. 最小可交付定义

如果时间有限，只做 P0，必须交付：

- `/agent/outline/status`
- `/agent/outline/search`
- `/agent/outline/sync`
- 旧路由 redirect
- Outline 导航分组
- Dry Run 明确提示
- 同步任务和明细可见
- 待补向量可见并可补齐
- RAG 可用状态可见

做到这里后，用户至少能完成：

```text
检查连接 → 搜索确认文档存在 → Dry Run → 正式同步 → 查看任务明细 → 补向量 → 验证 AI 可检索
```
