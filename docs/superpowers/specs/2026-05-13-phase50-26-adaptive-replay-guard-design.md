# Phase50.26 自适应规划回放对比与成本护栏设计

## 背景

Phase50.24 让 Runtime 能在第一轮证据不足时追加一轮只读工具。Phase50.25 把这件事沉淀到 Runtime 审计和 Ops 页面。

现在还缺一个关键问题的答案：自适应规划到底值不值。

如果只是多调用工具，不能证明收益；如果自适应总是在低价值场景触发，也会拖慢请求。因此 Phase50.26 聚焦两个能力：

- 回放同一条审计记录时，能对比“关闭自适应”和“按默认自适应”的执行差异。
- 在自适应规划阶段加入简单工具预算护栏，避免一次追加过多只读工具。

## 目标

1. Runtime replay 支持 `adaptiveMode=compare`，返回 baseline/off 与 adaptive/default 两次执行对比。
2. 自适应规划结果暴露 `evidenceBefore` 和 `evidenceAfter` 的精简摘要。
3. 自适应规划增加每轮最大追加工具数护栏，默认 1。
4. Java ops replay 代理透传 `adaptiveMode` 参数。
5. Ops 页面增加“自适应对比”操作和回放对比字段。
6. 保持现有普通执行、Plan 回放和执行回放兼容。

## 非目标

- 不实现真正 LangGraph conditional edge。
- 不新增多轮循环。
- 不引入复杂费用模型或计费系统。
- 不改变默认工具候选策略，只限制追加数量。
- 不改变一张图业务页面交互。

## 方案对比

### 方案 A：只在前端连续点两次执行回放

前端先调用执行回放，再带 `disableAdaptivePlanning=true` 调一次，自己比较。

优点：后端改动小。

缺点：前端要知道如何改 request options；两次请求没有统一 trace 和对比结构；Java 代理也难以表达。

### 方案 B：Runtime replay 增加 compare 模式

`debug_replay` 新增 `adaptiveMode=compare`。Runtime 内部基于同一审计 request 派生两个请求：

- baseline：`disableAdaptivePlanning=true`
- adaptive：保留原 options，按当前默认策略执行

返回 `compare` 摘要。

优点：对比语义在 Runtime 内闭合；前端只显示结果；方便后续加入更多对比字段。

缺点：一次操作会执行两次只读链路，耗时更高。

### 方案 C：审计记录保存原始 baseline

每次实际执行时同时保存“关闭自适应”的模拟结果。

优点：Ops 打开记录即可看差异。

缺点：正常用户请求会变重，且模拟结果可能与后续工具状态不一致。

### 选型

选择方案 B。只有在运维主动回放时才做双执行，成本可控，也不会影响线上正常请求。

## 后端设计

### 自适应预算护栏

新增配置：

```json
{
  "maxAdaptiveAddedTools": 1
}
```

环境变量：

- `SRMP_MAX_ADAPTIVE_ADDED_TOOLS`，默认 `1`，最小 `0`，最大不超过 `settings.max_tool_calls`。

请求 options 可覆盖：

```json
{
  "maxAdaptiveAddedTools": 1
}
```

`adaptive_planner.plan_adaptive_tools()` 在 `_filter_candidates()` 时同时受 `remaining_slots` 和 `maxAdaptiveAddedTools` 限制。超过预算的候选写入 `skippedToolNames`，状态仍可为 `PLANNED`，但 `reason` 和 summary 中要体现预算。

### Evidence 摘要

自适应 summary 增加：

```json
{
  "evidenceBefore": {
    "sufficient": false,
    "businessHitCount": 0,
    "knowledgeHitCount": 0,
    "toolSuccessCount": 1,
    "toolFailedCount": 0
  },
  "evidenceAfter": {
    "sufficient": true,
    "businessHitCount": 0,
    "knowledgeHitCount": 2,
    "toolSuccessCount": 2,
    "toolFailedCount": 0
  }
}
```

该摘要只取数字和布尔字段，避免把 toolSummary 全量塞进对比面板。

### Replay compare

`POST /api/srmp/langgraph/debug/replay/{recordId}` 新增 query：

```text
adaptiveMode=default | off | compare
```

行为：

- `default`：保持当前行为。
- `off`：执行回放时在 request options 中写入 `disableAdaptivePlanning=true`。
- `compare`：仅在 `execute=true` 时有效，执行 baseline/off 和 adaptive/default 两次，返回：

```json
{
  "execute": true,
  "adaptiveMode": "compare",
  "sourceRecordId": "...",
  "baseline": {"response": "..."},
  "adaptive": {"response": "..."},
  "compare": {
    "toolDelta": 1,
    "costDeltaMs": 32,
    "baselineAdaptiveStatus": "DISABLED",
    "adaptiveStatus": "EXECUTED",
    "evidenceImproved": true,
    "baselineEvidenceSufficient": false,
    "adaptiveEvidenceSufficient": true,
    "baselineKnowledgeHitCount": 0,
    "adaptiveKnowledgeHitCount": 2
  }
}
```

### Java 代理

`AgentOrchestratorOpsController.replay()` 增加 `adaptiveMode` query，并透传到 Runtime。

## 前端设计

### API

`replayOrchestratorRecord(recordId, execute, adaptiveMode)` 透传 query。

### Ops 页面

最近调用操作区增加“自适应对比”按钮：

- 调用 `replayRecord(row, true, 'compare')`
- 与执行回放共用 drawer
- 回放对比卡增加：
  - baseline/adaptive status
  - evidence 是否改善
  - 工具差值
  - 耗时差值
  - knowledge/business hit 差异

### 兼容

旧 replay response 没有 `compare` 时仍显示现有字段。

## 错误处理

- `adaptiveMode=compare` 且 `execute=false`：返回 400，提示 compare 需要执行回放。
- 审计记录缺 requestPayload：沿用现有 400。
- baseline 或 adaptive 任一执行异常：整体返回异常，Ops 展示错误详情。
- `maxAdaptiveAddedTools=0`：等价于禁用本轮追加，状态为 `SKIPPED_LIMIT`。

## 测试策略

### 后端

- `test_adaptive_planner.py`：`maxAdaptiveAddedTools=1` 时只追加一个候选，其他候选进入 skipped。
- `test_adaptive_workflow.py`：adaptive summary 包含 `evidenceBefore/evidenceAfter`。
- 新增 `test_replay_adaptive_compare.py`：helper 或 endpoint 返回 baseline/adaptive 对比结构。
- Java controller 源码测试：replay 透传 `adaptiveMode`。

### 前端

- `langGraphOpsAdaptive.test.mjs` 扩展：
  - API 支持 `adaptiveMode` 参数。
  - 页面包含“自适应对比”按钮。
  - `buildReplayCompare` 读取 `compare.evidenceImproved`、`toolDelta`、`costDeltaMs`。

## 验收标准

1. 自适应规划默认每轮最多追加 1 个工具。
2. Response 的 adaptivePlanning 有 evidence before/after 摘要。
3. Runtime replay 支持 `adaptiveMode=off` 和 `adaptiveMode=compare`。
4. Ops 页面能触发自适应对比并展示关键差异。
5. 普通执行、Plan 回放、执行回放保持兼容。
