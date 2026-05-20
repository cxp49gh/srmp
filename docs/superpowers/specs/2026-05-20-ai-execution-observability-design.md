# AI Execution Observability C 方案设计

## 背景

AI 养护助手已经从单轮问答扩展为“地图上下文 + 业务工具 + 知识检索 + 大模型生成 + 管理员排障”的执行链路。现状中，业务数据结构已经升级到项目、四级路段和对象化评定结果，但 AI 执行过程查询仍主要围绕旧 trace 日志和前端拼装字段展开。

本设计采用 C 方案：把 AI 执行过程从日志列表升级为正式的可观测业务系统，建立统一执行记录、统一业务范围、统一工具证据和统一管理员查询入口。

## 当前问题

1. 业务范围丢失或分散
   - GIS 页面已经使用 `projectId`、`sectionTier`、`routeCode`、`year`、`selectedLayers`。
   - AI 工具基类主要读取 `tenantId`、`routeCode`、`year`，没有稳定读取 `projectId`、四级路段层级、方向、桩号范围和对象类型。
   - `projectId` 常藏在 `mapContext.extra.rawContext.query`，不是一等字段。

2. 业务统计口径不一致
   - 地图统计按项目和层级过滤。
   - AI 工具查询病害、评定、区域摘要时没有完全复用 GIS 过滤语义。
   - 选中路线时，地图侧能展示关联路段、病害、评定，AI 侧可能只看到路线本身。

3. 执行过程数据源割裂
   - `ai_trace_log` 和 `ai_trace_step` 保存 legacy trace。
   - orchestrator runtime audit 保存更丰富的运行态信息。
   - live trace 保存执行中的步骤、工具、来源和 answerMeta。
   - 前端 `/agent/ai-traces` 需要猜测多种字段形状，容易出现“旧任务/旧接口/未返回 answerMeta”等误导提示。

4. 管理员无法快速定位问题
   - 无法直接判断业务工具查了什么范围。
   - 无法区分“业务数据为空”、“项目过滤缺失”、“sectionTier 排除了对象”、“知识库无 chunk”、“大模型返回空”、“工具失败”。
   - 无法按项目、路线、对象、工具、兜底原因进行稳定筛选。

5. 客户侧暴露技术诊断信息
   - `answerMeta`、`embedded chunks`、旧接口提示属于管理员排障语言，不应出现在客户执行流里。
   - 客户更关心当前对象、依据、结论和建议动作。

## 目标

1. 建立 AI 执行的唯一事实源，支持运行中、已完成、失败和旧记录兼容。
2. 将最新业务结构纳入 AI 执行范围，保证地图统计和 AI 工具统计口径一致。
3. 为管理员提供一屏排障能力：范围、步骤、工具、证据、模型、失败原因、原始数据。
4. 为客户隐藏技术诊断细节，只展示业务可理解的状态和依据。
5. 支持后续回放、质量分析、失败率统计和兜底率统计。

## 核心数据模型

### AiBusinessScope

`AiBusinessScope` 是 AI 执行的业务范围契约。前端、orchestrator、Java agent、业务工具和 trace 查询都使用同一套字段。

字段：

- `tenantId`
- `projectId`
- `routeCode`
- `year`
- `sectionTier`: `LINE | LEDGER | KM | HM`
- `contextScope`: `OBJECT | REGION | VIEWPORT | ROUTE | UNKNOWN`
- `objectType`
- `objectId`
- `assessmentObjectType`
- `direction`
- `startStake`
- `endStake`
- `bbox`
- `geometryType`
- `selectedLayers`

规则：

- GIS 页面发起的 AI 请求必须尽量带 `projectId` 和 `sectionTier`。
- 如果页面处于项目上下文但 AI 执行缺少 `projectId`，工具结果必须返回 `scopeWarnings`。
- 评定结果的项目过滤不能依赖 `assessment_result.project_id`，因为该表没有直接项目字段，需要通过路线或路段关联过滤。
- 桩号范围使用区间重叠语义：`record.start <= scope.end AND record.end >= scope.start`。

### AiExecutionRecord

`AiExecutionRecord` 是管理员查询的统一返回结构。

字段：

- `traceId`
- `recordId`
- `status`
- `action`
- `intent`
- `requestType`
- `provider`
- `model`
- `graphName`
- `startedAt`
- `finishedAt`
- `totalCostMs`
- `fallback`
- `fallbackReason`
- `errorMessage`
- `businessScope`
- `answer`
- `steps`
- `toolCalls`
- `evidence`
- `qualityFlags`
- `raw`

### Tool Evidence Contract

每个 AI 工具返回统一诊断字段：

- `queryScope`
- `totalCount`
- `returnedCount`
- `truncated`
- `summary`
- `items`
- `scopeWarnings`
- `failureReason`
- `rawArgs`

这些字段既用于大模型生成依据，也用于管理员排障。

## 数据库设计

新增正式执行表，保留旧 `ai_trace_log` / `ai_trace_step` 作为兼容来源。

### ai_execution_run

保存一次 AI 执行的主体信息：

- `id`
- `tenant_id`
- `trace_id`
- `request_type`
- `action`
- `intent`
- `mode`
- `provider`
- `model`
- `graph_name`
- `status`
- `fallback`
- `fallback_reason`
- `error_message`
- `total_cost_ms`
- `started_at`
- `finished_at`
- `created_at`
- `updated_at`
- `raw_request jsonb`
- `raw_response jsonb`

### ai_execution_scope

保存结构化业务范围：

- `execution_id`
- `tenant_id`
- `trace_id`
- `project_id`
- `route_code`
- `year`
- `section_tier`
- `context_scope`
- `object_type`
- `object_id`
- `assessment_object_type`
- `direction`
- `start_stake`
- `end_stake`
- `bbox jsonb`
- `geometry_type`
- `selected_layers jsonb`
- `scope_warnings jsonb`

### ai_execution_step

保存执行步骤：

- `execution_id`
- `tenant_id`
- `trace_id`
- `step_name`
- `step_label`
- `status`
- `cost_ms`
- `started_at`
- `finished_at`
- `error_message`
- `step_data jsonb`

### ai_execution_tool_call

保存工具调用和业务查询结果摘要：

- `execution_id`
- `tenant_id`
- `trace_id`
- `step_id`
- `tool_name`
- `status`
- `cost_ms`
- `total_count`
- `returned_count`
- `truncated`
- `query_scope jsonb`
- `result_summary jsonb`
- `raw_args jsonb`
- `raw_result jsonb`
- `error_message`

### ai_execution_evidence

保存证据来源：

- `execution_id`
- `tenant_id`
- `trace_id`
- `source_type`
- `source_name`
- `title`
- `route_code`
- `object_type`
- `object_id`
- `score`
- `payload jsonb`

### ai_execution_answer

保存回答和质量元数据：

- `execution_id`
- `tenant_id`
- `trace_id`
- `used_model`
- `answer_text`
- `answer_meta jsonb`
- `quality_flags jsonb`

## 后端 API 设计

新增统一查询接口：

- `GET /api/ai/executions`
- `GET /api/ai/executions/{traceId}`

查询参数：

- `status`
- `keyword`
- `projectId`
- `routeCode`
- `objectType`
- `action`
- `intent`
- `toolName`
- `fallback`
- `hasAnswerMeta`
- `hasBusinessEvidence`
- `startedFrom`
- `startedTo`
- `limit`

返回：

- 列表返回轻量 `AiExecutionRecord` 摘要。
- 详情返回完整 `AiExecutionRecord`。
- 如果没有新表记录，服务从 legacy trace + runtime audit 尽量聚合，返回 `legacy: true` 和诊断不足提示。

## 写入链路设计

1. Java map agent 请求进入时生成或继承 `traceId`。
2. Java agent tool gateway 和 orchestrator 都抽取 `AiBusinessScope`。
3. orchestrator 执行时写入 run、step、tool_call、evidence、answer。
4. 执行中的状态优先来自 live trace；完成后以数据库执行表为准。
5. legacy trace 在过渡期继续写入，作为兼容和回退。

## AI 业务查询设计

Java 侧新增统一查询上下文解析器，所有 AI 工具复用：

- 从 `mapContext`、`mapContext.extra`、`mapContext.extra.rawContext.query`、`actionInput`、`args`、`mapObject.raw` 解析 scope。
- 对直接业务表使用 `project_id`。
- 对 `assessment_result` 使用路线或路段关联过滤项目。
- 对 `sectionTier` 使用与 GIS 相同的 `object_type` 映射。
- 对路线对象补齐关联统计：路段、病害、评定。

先覆盖这些工具：

- `gis.queryRegionSummary`
- `gis.queryDiseases`
- `gis.queryAssessmentResults`
- `gis.queryDiseasesByStakeRange`
- `gis.queryNearbyObjects`
- `maintenance.generateSolution`

## 前端设计

`/agent/ai-traces` 改为 AI 执行中心。

列表筛选：

- 状态
- 项目
- 路线
- 对象类型
- 工具
- 兜底
- 是否有业务证据
- 是否有 answerMeta
- 时间范围

详情抽屉：

1. 执行摘要：状态、耗时、模型、action、intent。
2. 业务范围：项目、路线、年份、层级、对象、桩号、区域。
3. 工具与证据：每个工具的查询范围、命中数、返回数、失败原因。
4. 模型输出：answerMeta、来源、质量标记。
5. 排障建议：根据 failureReason 和 scopeWarnings 给出管理员动作。
6. 原始 JSON：仅管理员视图。

客户侧 AI 助手不显示技术诊断文案。执行中只显示业务状态，例如“正在查询当前路线关联病害和评定结果”。

## 兼容策略

- 新接口优先读取 `ai_execution_*`。
- 找不到新执行记录时，回退到 `ai_trace_log` / `ai_trace_step` 聚合。
- legacy 记录标记 `legacy: true`。
- legacy 记录不展示“未返回 answerMeta”作为错误，只展示“该记录诊断信息有限”。

## 风险与处理

1. 双写不一致
   - 新执行表作为管理员事实源。
   - legacy trace 仅兼容，不参与新功能判断。

2. 数据量增长
   - 结构化字段建索引。
   - 原始 payload 可后续配置留存天数。

3. 敏感信息泄露
   - `raw_request` 和 `raw_response` 后续可增加脱敏器。
   - 客户侧不展示 raw。

4. 旧执行记录诊断不足
   - 明确标记 legacy。
   - 不用旧数据推断不存在的 answerMeta 或证据。

5. 业务口径偏差
   - AI 工具必须复用与 GIS 一致的项目、层级、桩号过滤。
   - 通过测试固定“选中路线统计”和“地图统计”一致性。

## 验收标准

1. AI 执行记录能按项目、路线、状态、工具和兜底筛选。
2. 执行详情能看到结构化业务范围。
3. 工具调用能显示查询范围、命中总数、返回数量和失败原因。
4. 选择路线时，AI 查询的路段、病害、评定统计与地图统计口径一致。
5. 运行中的执行不显示 answerMeta 缺失警告。
6. 客户侧不暴露 `embedded chunks`、`answerMeta`、旧接口提示等技术文案。
7. legacy trace 可查看，但明确标记诊断信息有限。
