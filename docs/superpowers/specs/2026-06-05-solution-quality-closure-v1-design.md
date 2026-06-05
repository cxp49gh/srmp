# 方案生成质量闭环 v1 设计

## 目标

把方案任务的质量校验从“正文完整性检查”升级为“生成过程可信度快照”。v1 聚焦路线、路段、病害、评定、低分评定、区域这六类一张图方案，判断它们是否使用了正确模板、是否有业务证据、是否具备地图关联能力、是否使用或降级了大模型，并把结论展示给客户和管理员。

## 范围

- 复用现有 `ai_solution_task.quality_result`，不新增表。
- 复用 `template_meta`、`request_json`、`object_summary`、`map_object`、`ai_solution_source`。
- 质量快照只读分析已保存的任务，不重新调用大模型。
- 当前不做批量评测执行器，先把单任务质量结论做准。

## 后端设计

在 `AiSolutionQualityServiceImpl` 中扩展质量结果结构：

- `dimensions`: 面向业务和排障的维度结论。
  - `template`: 是否命中业务模板，是否兜底，缺变量数量。
  - `businessEvidence`: 是否有业务数据来源、工具证据、业务命中数。
  - `mapBinding`: 对象或来源是否能地图定位。
  - `llm`: 是否有大模型生成元信息，是否降级。
  - `scenario`: 当前方案类型与对象类型是否匹配。
- `checks`: 机器可读检查项，延续 `OK/WARN/ERROR`。
- `items`: 继续保留旧前端可读列表，避免兼容风险。
- `summary`: 一句话结论。

判定来源：

- 模板：`template_meta.fallback/matched/missingVariables/templateCode/templateName`。
- 业务证据：`ai_solution_source` 中 `BUSINESS_DATA/MAP_OBJECT/MAP_REGION`，以及 `request_json.sourceSummaries`、`object_summary.businessEvidence`。
- 地图关联：`map_object/object_summary` 中对象类型、对象 ID、路线、桩号、区域 trace；来源中有 `MAP_OBJECT/MAP_REGION` 也视为可定位。
- 大模型：`request_json.requestContext.answerMeta`、`request_json.trace.answerMeta`、`object_summary.answerMeta`、`sourceSummaries` 中的元信息；没有则给管理员提示“未记录模型元信息”，不直接判失败。
- 场景匹配：按 `solution_type + origin_type + object_type` 建立 v1 映射。

## 前端设计

增强 `SolutionQualityPanel.vue`：

- 顶部仍显示通过、评分、等级。
- 新增“质量快照”小卡片，展示模板、业务证据、地图关联、大模型、场景匹配。
- 检查项继续列表展示，客户能看懂，管理员能看到 code。

方案任务页保持现有按钮和布局，不新增入口。点击“质量校验”后刷新快照。

## 验收

- 路段计划命中 `SECTION_PLAN + MAP_OBJECT + ROAD_SECTION` 时，质量快照显示模板命中、场景匹配。
- 评定建议命中 `EVALUATION_UNIT_ADVICE + MAP_OBJECT + ASSESSMENT_RESULT` 时，不被误判为低分。
- 低分处置命中 `LOW_SCORE_TREATMENT + MAP_OBJECT + ASSESSMENT_RESULT` 时，低分场景匹配。
- 有 `MAP_OBJECT/MAP_REGION` 来源或对象坐标/桩号时，地图关联通过。
- 缺模板、缺业务证据、缺模型元信息时显示明确 WARN/ERROR，不再只给泛化评分。

## 测试

- 后端单测覆盖质量快照维度、场景匹配、模板兜底、地图定位。
- 前端静态测试覆盖 `SolutionQualityPanel` 渲染质量快照维度。
- 保留现有前端全量静态测试和构建验证。
