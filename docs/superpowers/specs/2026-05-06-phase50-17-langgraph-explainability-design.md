# Phase50.17 LangGraph 可解释执行体验设计

日期：2026-05-06
状态：待用户评审

## 背景

Phase50.14 到 Phase50.16 已经让 `srmp-ai-orchestrator` 在地图智能体场景中具备 native 功能对齐、真实 LLM 调用、Tool Gateway 合约校验、运行时审计和回放能力。当前问题是这些优势主要停留在后端和 Ops 页面里，前端用户在普通问答、单对象分析、路线分析、区域分析中看到的仍然只是回答正文，难以判断：

- 这次是否真的走了 LangGraph。
- 识别了什么意图。
- 调用了哪些工具，命中了多少业务数据或知识库。
- LLM 是否成功、是否重试、是否降级。
- 哪一步慢、哪一步失败、能否回放复现。

Phase50.17 的目标是把 LangGraph 的优势产品化为统一的“AI 执行过程”体验，让业务用户能看懂结果来源，让开发/运维能快速诊断，让后续移除 native 前有足够可观测证据。

## 目标

1. 普通问答、单对象分析、路线分析、区域分析都支持查看同一套 `AI 执行过程`。
2. 前端统一展示 `answerMeta`、意图、节点步骤、工具调用、知识来源、质量保护和耗时。
3. Ops 页面把运行记录、回放、导出从原始 JSON 调试升级为可读的执行过程视图。
4. answerMeta 的准确性可通过 trace 步骤、工具结果、来源数量和质量保护结果交叉验证。
5. 不改变现有核心编排逻辑，只增强可解释性、诊断性和演示性。

## 非目标

- 本阶段不移除 native fallback。
- 本阶段不重写 LangGraph 节点、工具规划或 Prompt 策略。
- 本阶段不新增业务写工具，不扩大 Tool Gateway 权限。
- 本阶段不改数据库结构。
- 前端不做整页重设计，只在现有聊天、地图和 Ops 页面中补齐统一展示。
- 诊断导出不展示 API Key、Cookie、Authorization 等敏感字段。

## 现状分析

### 前端

- `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue` 已有抽屉，但只展示 TraceId、类型、模式、状态、总耗时和简单 timeline。它没有展示 answerMeta、工具明细、证据来源、质量保护、回放入口。
- `srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue` 只区分 LLM 和本地降级，对 LangGraph 的 `llmStatus`、`llmModel`、`retriedWithCompactPrompt`、`qualityFallback`、`fallbackReason` 展示不足。
- `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue` 已保存 `answerMeta`、`trace`、`toolResults`、`sources`，适合升级为统一执行过程入口。
- `srmp-web-ui/src/views/agent/AiChatPage.vue` 目前只在消息里挂 trace，没有把 answerMeta 和来源信息作为统一展示对象。
- `srmp-web-ui/src/views/gis/OneMap.vue`、`SolutionPreviewDialog.vue` 已接入区域/方案 trace，可以复用同一抽屉。
- `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue` 已有运行摘要、Recent、record、replay、export，但详情仍主要以 JSON 展示，演示效果弱。

### 后端

- `srmp-ai-orchestrator/app/workflow.py` 返回 `trace.steps`、`data.answerMeta`、`toolResults`、`sources`、`quality`、`intent`、`nodeFlow`。
- `srmp-ai-orchestrator/app/observability.py` 已保存 `steps`、`toolResults`、`requestPayload`、`responsePreview`、工具数量、来源数量、耗时和可回放标记。
- `srmp-ai-orchestrator/app/main.py` 已提供 recent、record、export、plan replay、execute replay 等接口。
- `srmp-agent` 已通过 `/api/agent/orchestrator/ops/*` 代理这些接口。

这些能力已经足够支撑 Phase50.17。后端只需在必要时补少量摘要字段，主要改动应放在前端展示层和静态校验脚本。

## 方案选择

### 方案 A：只加强现有 Trace 抽屉

改动最小，快速见效。但 Ops 页面仍然偏调试工具，answerMeta 与回放无法形成完整演示链路。

### 方案 B：新增一套 LangGraph 专属演示页面

展示效果强，但会和现有聊天、地图、区域方案入口割裂，普通用户仍看不到每次回答的执行过程。

### 方案 C：统一执行过程组件，所有入口复用

以 `AiTraceDrawer` 为统一入口，抽出归一化工具和展示子组件；聊天、地图、区域方案、Ops 运行记录都使用同一套视图。它能同时满足业务解释、开发诊断和验收演示，且不改变核心编排。

推荐采用方案 C。

## 统一数据模型

前端新增一个归一化视图模型，命名建议为 `AiExecutionSnapshot`。它只服务展示，不反向影响后端协议。

```ts
interface AiExecutionSnapshot {
  summary: {
    traceId?: string
    recordId?: string
    provider?: string
    intent?: string
    requestType?: string
    mapMode?: string
    status?: string
    costMs?: number
    fallback?: boolean
  }
  answerMeta: {
    answerSource?: string
    answerSourceLabel?: string
    llmSuccess?: boolean
    llmStatus?: string
    llmModel?: string
    retriedWithCompactPrompt?: boolean
    fallbackReason?: string
    qualityFallback?: boolean
    notice?: string
  }
  steps: Array<{
    key: string
    label: string
    status: string
    costMs?: number
    count?: number
    phase?: string
    error?: string
    data?: Record<string, any>
  }>
  tools: Array<{
    name: string
    success: boolean
    count?: number
    costMs?: number
    error?: string
  }>
  evidence: {
    sourceCount?: number
    knowledgeCount?: number
    businessCount?: number
    outlineCount?: number
    sources?: Array<Record<string, any>>
  }
  quality?: Record<string, any>
  raw?: Record<string, any>
}
```

归一化输入支持三类结构：

- 聊天/地图接口返回：`trace` + `data.answerMeta` + `toolResults` + `sources`。
- 区域/方案接口返回：`trace` + `answerMeta` + `qualityCheck`。
- Ops record 返回：`steps` + `toolResults` + `responsePreview` + `requestPayload`。

缺字段时显示“未返回”，但不阻断抽屉打开。

## 前端设计

### 1. 统一 `AiTraceDrawer`

将抽屉标题从 `AI Trace` 调整为 `AI 执行过程`。抽屉内部按四块展示：

1. 总览：provider、intent、状态、总耗时、工具成功数、来源数、是否降级、TraceId/RecordId。
2. 回答来源：复用并增强 `AnswerSourceAlert`，展示 answerSource、LLM 状态、模型、重试、降级原因、质量保护状态。
3. 执行时间线：展示 LangGraph 节点或 Java Trace 步骤，包含步骤名、状态、耗时、命中数量和错误信息。关键步骤的数据摘要可折叠查看。
4. 工具与证据：展示工具调用结果、知识库来源数量、业务数据命中数量，保留原始 JSON 折叠区用于排障。

`AiTraceDrawer` 继续兼容现有 `trace` prop，同时新增可选 prop：

- `answerMeta`
- `toolResults`
- `sources`
- `record`
- `replayResult`

这样旧入口不需要一次性大改，新增入口可以传入更完整上下文。

### 2. `AnswerSourceAlert` 增强

新增对以下字段的展示：

- `answerSource`
- `llmStatus`
- `llmModel`
- `retriedWithCompactPrompt`
- `fallbackReason`
- `qualityFallback`

显示规则：

- `llmSuccess=true` 且 `answerSource=LLM`：绿色，说明大模型成功生成。
- `llmSuccess=false` 或 `answerSource` 为降级类：黄色，说明降级原因。
- `qualityFallback=true`：黄色，说明正文经过质量保护兜底。
- 缺少 answerMeta：灰色提示“未返回 answerMeta”，避免误判为 LLM 成功。

### 3. 普通问答页

`AiChatPage.vue` 的 assistant 消息结构补齐：

- `answerMeta`
- `trace`
- `toolResults`
- `sources`

每条 AI 回复显示 `AI 执行过程` 按钮。点击后打开同一 `AiTraceDrawer`，不再只传 trace。

### 4. 一张图聊天浮窗

`AgentChatFloat.vue` 已有 answerMeta 和证据数据，改为：

- 消息标签展示：意图、回答来源、工具数量、来源数量、是否重试、是否降级。
- `AiTraceButton` 文案统一为 `AI 执行过程`。
- 打开抽屉时传入 trace、answerMeta、toolResults、sources。

### 5. 区域与方案预览

`OneMap.vue`、`SolutionPreviewDialog.vue` 继续使用同一抽屉：

- 区域分析展示 region geometry parse、spatial query、statistics、hotspot、business analysis、knowledge retrieve、solution generate、quality check 等步骤。
- 区域养护建议展示模板信息、LLM 状态、质量检查和保存任务关联 Trace。
- 与单对象方案保持一致，不新增第二套区域专属 Trace UI。

### 6. LangGraph Ops 页面

`LangGraphOpsPage.vue` 保留原有运行摘要和 recent 表格，但增强三类操作：

- 查看执行过程：获取 record 后用统一抽屉展示，而不是只打开 JSON。
- 回放对比：Plan 回放或执行回放后，展示“原始记录 vs 回放结果”的状态、意图、工具数、来源数、耗时和 LLM 状态。
- 导出诊断：继续复制 JSON，但在预览中标注这是脱敏快照。

Ops 页面可以保留原始 JSON 对话框作为高级排障入口，但默认入口应是可读视图。

## answerMeta 准确性判断

前端不直接相信单一字段，而是展示交叉验证线索：

- `answerMeta.llmSuccess=true` 应与 `llm_answer` 步骤 `SUCCESS`、`answerSource=LLM` 一致。
- `answerMeta.llmStatus=SUCCESS` 应展示 `llmModel` 和是否压缩重试。
- `toolSuccessCount/toolFailedCount` 应与 `toolResults` 列表一致。
- `sourceCount` 应与 `sources` 或知识库工具返回数量一致。
- 若 `quality_guard` 修改了正文，应展示质量保护摘要。
- 若出现不一致，只提示“元信息与 Trace 存在差异”，不阻止用户查看原始数据。

## 后端微调

优先不改后端协议。如果实现时发现 Ops recent 表格需要更快展示 answerMeta，可在 `RuntimeAuditStore.record_success` 顶层增加脱敏摘要字段：

- `answerSource`
- `llmSuccess`
- `llmStatus`
- `llmModel`
- `qualityFallback`

这些字段来自 `response.data.answerMeta` 和 `response.data.quality`，不包含 prompt、API Key 或完整回答。Java 代理无需新增接口。

## 错误处理

- Trace 缺失：按钮不显示；如果入口强制打开，抽屉显示空状态。
- answerMeta 缺失：展示“未返回 answerMeta”，并提示可能是旧任务、旧接口或未经过 LangGraph 管线。
- record 不可回放：回放按钮置灰，并说明缺少 requestPayload。
- 回放失败：保留错误信息、TraceId 和原始 JSON 折叠区。
- 工具失败：时间线和工具区都标红，显示工具名、错误信息和耗时。
- LLM 超时或失败：展示 `llmStatus`、降级原因、是否使用业务分析兜底。

## 验收演示

完成后用四类场景演示 LangGraph 优势：

1. 普通问答：看到意图识别、知识库检索、LLM 生成和质量保护。
2. 单对象分析：看到地图对象上下文、GIS 工具、知识库来源和 LLM 生成。
3. 路线分析：看到业务数据工具、路线上下文、证据汇总。
4. 区域分析：看到区域解析、空间查询、统计、热点识别、知识检索、建议生成、质量检查。

Ops 页面演示：

- Recent 记录可打开可读执行过程。
- Plan 回放可展示计划节点。
- 执行回放可展示新旧结果对比。
- 导出诊断仍可用于外部排障。

## 测试计划

1. 新增 `scripts/check-phase50-17-langgraph-explainability.sh`，检查关键文件和字段接入：
   - `AiTraceDrawer` 展示 answerMeta、工具、来源、质量和原始数据折叠区。
   - `AgentChatFloat`、`AiChatPage`、`OneMap`、`SolutionPreviewDialog` 都传入统一执行过程数据。
   - `LangGraphOpsPage` 支持 record 详情、回放结果和统一抽屉。
2. 运行 `npm --prefix srmp-web-ui run build`。
3. 继续运行既有回归脚本：
   - `bash scripts/check-phase50-14-langgraph-map-agent-parity.sh`
   - `bash scripts/check-phase50-15-langgraph-native-parity.sh`
   - `bash scripts/check-phase50-16-langgraph-llm-live-closure.sh`
4. 如后端增加 RuntimeAuditStore 摘要字段，运行：
   - `srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py`
   - `SRMP_PHASE50_16_LIVE=1 SRMP_LANGGRAPH_USE_LLM=true bash scripts/check-phase50-16-langgraph-llm-live-closure.sh`

## 验收标准

- 用户在普通问答、单对象分析、路线分析、区域分析里都能打开 `AI 执行过程`。
- 抽屉能清楚展示回答来源、LLM 状态、模型、工具调用、来源数量、步骤耗时、错误和质量保护。
- answerMeta 缺失或不一致时有明确提示，不再静默显示“未返回模板信息”一类误导性空状态。
- Ops recent 记录默认能以可读视图查看，仍保留原始 JSON 排障能力。
- Plan 回放和执行回放能在页面上形成可比较结果。
- 前端构建通过，Phase50.14/15/16 回归脚本继续通过。
- 未引入敏感信息展示，未扩大工具写权限。
