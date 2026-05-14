# Phase50.25 自适应规划运维观测闭环设计

## 背景

Phase50.24 已经在 Runtime 主链路中加入一轮只读自适应工具规划：

```text
tool_execute -> evidence_fuse
-> adaptive_tool_planning -> adaptive_tool_execute -> adaptive_evidence_fuse
-> answer_generate
```

返回结构中已经包含 `adaptivePlanning`，计划执行对比也能解释自适应追加工具。但这部分信息主要留在单次 response、trace 和一张图计划抽屉里，运维侧仍然缺少整体可见性：

- Ops 摘要看不到最近多少请求触发过自适应补充。
- 最近调用表无法快速筛选或判断某次请求是否追加了工具。
- Runtime 配置快照没有暴露 `SRMP_ADAPTIVE_PLANNING_ENABLED` 和 `SRMP_MAX_ADAPTIVE_ITERATIONS`。
- 导出的诊断包无法从 summary 层判断自适应规划是否异常增多。

Phase50.25 的目标是把 Phase50.24 的单次能力变成可运维的观测闭环。

## 目标

1. Runtime 安全配置和健康快照暴露自适应规划开关、最大轮次。
2. 审计记录保存每次请求的 `adaptivePlanningStatus`、追加工具数、追加工具名和原因。
3. Runtime summary 增加自适应状态桶、触发次数、追加工具桶。
4. Ops 页面增加自适应规划指标卡和最近调用列。
5. Ops 导出和 snapshot 自动包含自适应规划摘要。
6. 保持 Phase50.24 的执行行为不变，不新增工具调用策略。

## 非目标

- 不把 no-op 节点升级为真正 LangGraph conditional edge。
- 不新增多轮自适应循环。
- 不在业务一张图页面增加新的用户开关。
- 不改变 Java Tool Gateway 工具契约。
- 不新增数据库持久化表。
- 不让 Plan Debug 执行工具；Plan Debug 仍然只规划不执行。

## 方案对比

### 方案 A：只在前端从 trace 里临时解析

Ops 页面打开记录时，从 `responsePreview.data.adaptivePlanning` 临时读取状态。

优点：

- 后端改动最小。

缺点：

- summary、recent、export 无法直接聚合。
- 前端要理解 responsePreview 的压缩结构。
- 对排查“最近自适应触发是否异常增多”帮助有限。

### 方案 B：在审计记录中沉淀自适应摘要

RuntimeAuditStore 在 `record_success()` 时把 `adaptivePlanning` 提炼成平铺字段，summary/recent/export 都基于审计记录计算。

优点：

- 与现有 intent/action/tool bucket 模式一致。
- 前端读取简单，最近调用列表可直接展示。
- 导出诊断包自然包含聚合数据。
- 不影响 Runtime 执行逻辑。

缺点：

- 需要补 observability 单元测试。
- 旧审计记录没有这些字段，需要前端兼容空值。

### 方案 C：新增自适应规划专属观测接口

新增 `/observability/adaptive-planning`，单独返回统计、最近触发记录和追加工具分布。

优点：

- 接口语义清晰。
- 后续可以扩展更多成本统计。

缺点：

- 对当前小范围需求偏重。
- Ops 页面要多一次请求。
- 与现有 summary/export 数据重复。

### 选型

选择方案 B。它沿用 RuntimeAuditStore 的既有审计模型，能把自适应规划纳入 summary、recent、snapshot 和 export，同时避免新增接口和执行行为。

## 后端设计

### 安全配置

`_safe_runtime_config()` 增加：

```json
{
  "adaptivePlanningEnabled": true,
  "maxAdaptiveIterations": 1
}
```

`_config_warnings()` 增加轻量告警：

- `ADAPTIVE_WITH_LOW_TOOL_LIMIT`：自适应规划开启但 `maxToolCalls < 2`，几乎没有追加空间。
- `ADAPTIVE_ITERATIONS_DISABLED`：自适应规划开启但 `maxAdaptiveIterations <= 0`。

### 审计记录

`RuntimeAuditStore.record_success()` 从 response data/trace 中读取 `adaptivePlanning`，写入平铺字段：

```json
{
  "adaptivePlanningStatus": "EXECUTED",
  "adaptivePlanningEnabled": true,
  "adaptiveAddedToolCount": 1,
  "adaptiveAddedToolNames": ["knowledge.retrieve"],
  "adaptivePlanningReason": "业务工具未命中，追加知识检索补充解释依据。"
}
```

旧记录兼容：字段缺失时前端显示 `-` 或 `0`。

### Summary

`RuntimeAuditStore.summary()` 增加：

```json
{
  "adaptivePlanning": {
    "executedCount": 3,
    "skippedCount": 17,
    "disabledCount": 0,
    "addedToolCount": 4,
    "statusBuckets": {
      "EXECUTED": 3,
      "SKIPPED_SUFFICIENT": 12,
      "SKIPPED_NO_CANDIDATE": 5
    },
    "addedToolBuckets": {
      "knowledge.retrieve": 3,
      "gis.queryRegionSummary": 1
    }
  }
}
```

聚合规则：

- `executedCount`：`adaptivePlanningStatus == EXECUTED`。
- `skippedCount`：状态以 `SKIPPED_` 开头。
- `disabledCount`：状态为 `DISABLED`。
- `addedToolCount`：所有 `adaptiveAddedToolNames` 长度总和。

## 前端设计

### 指标卡

`LangGraphOpsPage.vue` 的指标区新增一张“自适应规划”卡：

- 状态 tag：有执行为 `warning`，无执行为 `info`，Runtime 关闭为 `info`。
- 主数值：`executedCount`。
- 描述：`skippedCount`、`disabledCount`、`addedToolCount`、`maxAdaptiveIterations`。

### 配置健康

配置健康面板增加一行：

```text
自适应规划  enabled=true；max=1
```

### 最近调用表

最近 LangGraph 调用表增加一列：

- `自适应`
- `EXECUTED +1`、`SKIPPED_SUFFICIENT`、`DISABLED` 或 `-`
- 若有追加工具，通过 tooltip 显示工具名。

### 数据兼容

前端 helper 只从 `runtimeSummary.adaptivePlanning`、`config.safeConfig` 和 recent row 平铺字段读取。字段缺失时：

- 指标显示 `0`
- 配置显示 `-`
- 最近调用列显示 `-`

## 错误处理

- response 中没有 `adaptivePlanning`：审计记录写空状态，不影响 record_success。
- `adaptivePlanning.addedToolNames` 不是数组：按空数组处理。
- 旧审计记录缺字段：summary 聚合跳过，前端显示空态。
- 配置值异常：通过 config warnings 暴露，不阻断 ready/health。

## 测试策略

### 后端

新增或扩展 `srmp-ai-orchestrator/tests/test_observability.py`：

- 记录包含 `adaptivePlanning.status=EXECUTED` 的响应后，recent record 有平铺字段。
- summary 返回 `adaptivePlanning.executedCount`、`addedToolBuckets`。
- 没有 adaptivePlanning 的旧式响应不会报错。

扩展 `test_debug_plan_preview.py` 或新增 config 测试：

- `/api/srmp/langgraph/runtime/config` 的 `safeConfig` 包含 `adaptivePlanningEnabled` 和 `maxAdaptiveIterations`。

### 前端

新增 `srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs`：

- 页面源码包含自适应指标卡。
- 页面源码包含 recent 表的自适应列。
- `runPlan` 不把 adaptive options 混进 Plan Debug 执行行为。

### 验证

- 后端 unittest：observability、plan execution、adaptive workflow、debug config。
- 前端 Node tests：新增 ops adaptive 测试和既有 focused tests。
- `npm run build`。

## 验收标准

1. Runtime config/snapshot 能看到自适应规划开关和最大轮次。
2. Runtime summary 能看到自适应规划执行次数、跳过次数、追加工具分布。
3. 最近调用表能显示每条记录的自适应状态。
4. 旧记录和缺字段响应不报错。
5. 不改变 Phase50.24 的工具执行策略和回答生成行为。
