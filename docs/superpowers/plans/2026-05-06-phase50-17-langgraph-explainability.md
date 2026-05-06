# Phase50.17 LangGraph Explainability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose LangGraph's explainability advantages through one reusable `AI 执行过程` experience across ordinary chat, GIS object/route/region analysis, solution previews, and LangGraph Ops replay.

**Architecture:** Add a small front-end normalization layer that converts chat responses, region/solution responses, and Ops audit records into one display model. Upgrade the existing `AiTraceDrawer` and `AnswerSourceAlert` to render that model, then wire all existing entry points to pass the richer context. Add one static verification script plus the existing build/regression checks.

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus, Vite/vue-tsc, Bash verification scripts, existing srmp-ai-orchestrator observability APIs.

---

## File Structure

- Create `srmp-web-ui/src/views/agent/components/aiExecution.ts`
  - Owns `AiExecutionSnapshot` types and all normalization helpers.
  - Has no Vue dependency so it remains easy to compile and reason about.
- Modify `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
  - Becomes the unified `AI 执行过程` drawer.
  - Uses `toAiExecutionSnapshot()` and renders summary, answer source, timeline, tools/evidence, quality, raw JSON.
- Modify `srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue`
  - Shows LLM status/model/retry/fallback/quality details.
- Modify `srmp-web-ui/src/views/agent/components/AiTraceButton.vue`
  - Renames visible text from `查看 Trace` to `AI 执行过程`.
  - Allows richer execution payloads without breaking existing `trace` only callers.
- Modify `srmp-web-ui/src/views/agent/AiChatPage.vue`
  - Stores `answerMeta`, `toolResults`, and `sources` on assistant messages.
  - Opens the drawer with the full message context.
- Modify `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
  - Adds tool/source/retry/fallback tags.
  - Opens the drawer with full message context.
- Modify `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
  - Passes `answerMeta`, `toolResults`, `sources`, and solution data to the drawer.
- Modify `srmp-web-ui/src/views/gis/OneMap.vue`
  - Passes region solution metadata into the drawer.
- Modify `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`
  - Uses the same drawer for runtime records.
  - Keeps raw JSON dialog as advanced diagnostics.
  - Shows replay comparison summary after plan/execute replay.
- Optionally modify `srmp-ai-orchestrator/app/observability.py`
  - Only if Ops recent rows need top-level `answerSource`, `llmStatus`, or `qualityFallback`.
- Create `scripts/check-phase50-17-langgraph-explainability.sh`
  - Static checks for wiring plus `npm --prefix srmp-web-ui run build`.

## Task 1: Add AI Execution Normalization Model

**Files:**
- Create: `srmp-web-ui/src/views/agent/components/aiExecution.ts`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Create the normalization module**

Add this file:

```ts
export interface AiExecutionStep {
  key: string
  label: string
  status: string
  costMs?: number
  count?: number | string
  phase?: string
  error?: string
  data?: Record<string, any>
}

export interface AiExecutionTool {
  name: string
  success: boolean
  count?: number | string
  costMs?: number
  error?: string
}

export interface AiExecutionSnapshot {
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
    toolTotalCount?: number
    toolSuccessCount?: number
    toolFailedCount?: number
    sourceCount?: number
  }
  answerMeta: Record<string, any>
  steps: AiExecutionStep[]
  tools: AiExecutionTool[]
  evidence: {
    sourceCount?: number
    knowledgeCount?: number
    businessCount?: number
    outlineCount?: number
    sources: Record<string, any>[]
  }
  quality?: Record<string, any>
  raw: Record<string, any>
  warnings: string[]
}

export interface AiExecutionInput {
  trace?: Record<string, any> | null
  answerMeta?: Record<string, any> | null
  toolResults?: any[] | null
  sources?: any[] | null
  record?: Record<string, any> | null
  replayResult?: Record<string, any> | null
  solution?: Record<string, any> | null
}

export function toAiExecutionSnapshot(input: AiExecutionInput): AiExecutionSnapshot | null {
  const trace = input.trace || extractTrace(input)
  const record = input.record || null
  const responsePreview = asRecord(record?.responsePreview)
  const responseData = asRecord(responsePreview?.data)
  const answerMeta = firstRecord(
    input.answerMeta,
    input.solution?.answerMeta,
    input.solution?.answer_meta,
    responseData.answerMeta,
    responsePreview.answerMeta,
    trace?.answerMeta
  )
  const toolResults = firstArray(input.toolResults, input.solution?.toolResults, responsePreview.toolResults, record?.toolResults)
  const sources = firstArray(input.sources, input.solution?.sources, input.solution?.knowledgeSources, responsePreview.sources, responseData.sources, responseData.knowledgeSources)
  const steps = normalizeSteps(firstArray(trace?.steps, record?.steps, responsePreview.trace?.steps))
  const tools = normalizeTools(toolResults)
  const quality = firstRecord(input.solution?.quality, input.solution?.qualityCheck, responseData.quality, responsePreview.quality, trace?.quality)
  const summary = {
    traceId: stringValue(trace?.traceId || trace?.trace_id || record?.traceId),
    recordId: stringValue(record?.id),
    provider: stringValue(trace?.orchestratorProvider || responseData.orchestratorProvider || record?.provider || record?.engine),
    intent: stringValue(responseData.intent || responsePreview.intent || record?.intent),
    requestType: stringValue(trace?.requestType || trace?.request_type || record?.requestType),
    mapMode: stringValue(record?.mapMode || trace?.mode || responseData.mapMode),
    status: stringValue(record?.status || trace?.status || responsePreview.status),
    costMs: numberValue(trace?.costMs ?? trace?.totalCostMs ?? trace?.total_cost_ms ?? record?.costMs),
    fallback: booleanValue(answerMeta.fallback ?? trace?.fallback ?? record?.fallbackLike),
    toolTotalCount: numberValue(responseData.toolTotalCount ?? record?.toolTotalCount ?? tools.length),
    toolSuccessCount: numberValue(responseData.toolSuccessCount ?? record?.toolSuccessCount ?? tools.filter((item) => item.success).length),
    toolFailedCount: numberValue(responseData.toolFailedCount ?? record?.toolFailedCount ?? tools.filter((item) => !item.success).length),
    sourceCount: numberValue(responseData.sourceCount ?? record?.sourceCount ?? sources.length)
  }
  const warnings = buildWarnings(answerMeta, steps, summary.sourceCount, sources.length)
  if (!trace && !record && !input.solution && !input.replayResult) return null
  return {
    summary,
    answerMeta,
    steps,
    tools,
    evidence: {
      sourceCount: summary.sourceCount,
      knowledgeCount: countSources(sources, 'KNOWLEDGE'),
      businessCount: countSources(sources, 'BUSINESS'),
      outlineCount: countSources(sources, 'OUTLINE'),
      sources
    },
    quality,
    raw: compactRaw(input),
    warnings
  }
}

function extractTrace(input: AiExecutionInput): Record<string, any> | null {
  return firstRecord(input.solution?.trace, input.replayResult?.trace, input.replayResult?.data?.trace)
}

function normalizeSteps(steps: any[]): AiExecutionStep[] {
  return steps.map((step, index) => {
    const item = asRecord(step)
    const key = stringValue(item.key || item.id || item.step_name || item.name || `step-${index}`) || `step-${index}`
    return {
      key,
      label: stringValue(item.step_label || item.label || item.step_name || item.name || key) || key,
      status: stringValue(item.status || 'UNKNOWN') || 'UNKNOWN',
      costMs: numberValue(item.cost_ms ?? item.costMs ?? item.elapsedMs),
      count: item.hit_count ?? item.count ?? item.resultCount,
      phase: stringValue(item.phase || item.node || item.group),
      error: stringValue(item.error_message || item.error),
      data: asRecord(item.data || item.detail || item.details)
    }
  })
}

function normalizeTools(tools: any[]): AiExecutionTool[] {
  return tools.map((tool, index) => {
    const item = asRecord(tool)
    const name = stringValue(item.name || item.tool || item.toolName || item.type || `tool-${index}`) || `tool-${index}`
    const success = item.success !== false && String(item.status || '').toUpperCase() !== 'FAILED'
    return {
      name,
      success,
      count: item.count ?? item.hitCount ?? item.resultCount ?? item.total,
      costMs: numberValue(item.costMs ?? item.cost_ms ?? item.elapsedMs),
      error: stringValue(item.error || item.errorMessage || item.message)
    }
  })
}

function buildWarnings(answerMeta: Record<string, any>, steps: AiExecutionStep[], sourceCount?: number, actualSources = 0): string[] {
  const warnings: string[] = []
  const llmStep = steps.find((step) => step.key === 'llm_answer' || step.label.includes('LLM') || step.label.includes('大模型'))
  if (answerMeta.llmSuccess === true && llmStep && llmStep.status !== 'SUCCESS') {
    warnings.push('answerMeta 显示 LLM 成功，但 LLM 步骤不是 SUCCESS。')
  }
  if (typeof sourceCount === 'number' && actualSources > 0 && sourceCount !== actualSources) {
    warnings.push('来源数量与实际来源列表数量存在差异。')
  }
  if (!Object.keys(answerMeta).length) {
    warnings.push('未返回 answerMeta，可能是旧任务、旧接口或未经过 LangGraph 管线。')
  }
  return warnings
}

function countSources(sources: Record<string, any>[], type: string): number {
  return sources.filter((item) => String(item.sourceType || item.type || '').toUpperCase().includes(type)).length
}

function firstRecord(...values: any[]): Record<string, any> {
  for (const value of values) {
    const record = asRecord(value)
    if (Object.keys(record).length) return record
  }
  return {}
}

function firstArray(...values: any[]): any[] {
  for (const value of values) {
    if (Array.isArray(value)) return value
  }
  return []
}

function asRecord(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringValue(value: any): string | undefined {
  if (value === undefined || value === null || value === '') return undefined
  return String(value)
}

function numberValue(value: any): number | undefined {
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}

function booleanValue(value: any): boolean | undefined {
  if (value === undefined || value === null || value === '') return undefined
  if (typeof value === 'boolean') return value
  return String(value).toLowerCase() === 'true'
}

function compactRaw(input: AiExecutionInput): Record<string, any> {
  return {
    trace: input.trace || undefined,
    answerMeta: input.answerMeta || undefined,
    toolResults: input.toolResults || undefined,
    sources: input.sources || undefined,
    record: input.record || undefined,
    replayResult: input.replayResult || undefined,
    solution: input.solution || undefined
  }
}
```

- [ ] **Step 2: Run TypeScript build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected:

```text
vite v...
✓ built in ...
```

- [ ] **Step 3: Commit**

```bash
git add srmp-web-ui/src/views/agent/components/aiExecution.ts
git commit -m "feat: normalize AI execution traces"
```

## Task 2: Upgrade Answer Source and Trace Button Components

**Files:**
- Modify: `srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue`
- Modify: `srmp-web-ui/src/views/agent/components/AiTraceButton.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Replace `AnswerSourceAlert.vue` with an expanded source summary**

Use this structure:

```vue
<template>
  <el-alert
    v-if="visible"
    class="answer-source-alert"
    :type="alertType"
    show-icon
    :closable="false"
  >
    <template #title>{{ title }}</template>
    <div class="notice">{{ notice }}</div>
    <div class="meta-row">
      <el-tag v-if="meta?.answerSource" size="small">{{ meta.answerSource }}</el-tag>
      <el-tag v-if="meta?.llmStatus" size="small" :type="meta.llmSuccess ? 'success' : 'warning'">LLM {{ meta.llmStatus }}</el-tag>
      <el-tag v-if="meta?.llmModel" size="small" type="info">{{ meta.llmModel }}</el-tag>
      <el-tag v-if="meta?.retriedWithCompactPrompt" size="small" type="warning">压缩重试</el-tag>
      <el-tag v-if="meta?.qualityFallback" size="small" type="warning">质量兜底</el-tag>
    </div>
  </el-alert>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ meta?: Record<string, any> | null; allowEmpty?: boolean }>()

const visible = computed(() => props.allowEmpty || Boolean(props.meta && Object.keys(props.meta).length))
const meta = computed(() => props.meta || {})

const alertType = computed(() => {
  if (!Object.keys(meta.value).length) return 'info'
  if (meta.value.qualityFallback) return 'warning'
  if (meta.value.llmSuccess && meta.value.answerSource === 'LLM') return 'success'
  return 'warning'
})

const title = computed(() => {
  if (!Object.keys(meta.value).length) return '未返回 answerMeta'
  return meta.value.answerSourceLabel || sourceLabel(meta.value.answerSource)
})

const notice = computed(() => {
  if (!Object.keys(meta.value).length) return '当前结果缺少 answerMeta，可能是旧任务、旧接口或没有经过 LangGraph 管线。'
  if (meta.value.notice || meta.value.answerNotice) return meta.value.notice || meta.value.answerNotice
  if (meta.value.llmSuccess && meta.value.answerSource === 'LLM') return '本次回答由大模型成功生成。'
  const reason = meta.value.fallbackReason || meta.value.llmStatus || '大模型未返回有效内容'
  return `本次回答经过降级或兜底处理。原因：${reason}`
})

function sourceLabel(source?: string) {
  if (source === 'LLM') return '大模型返回'
  if (source === 'LOCAL_FALLBACK') return '本地知识库/业务数据降级返回'
  if (source === 'BUSINESS_ANALYSIS_FALLBACK') return '业务分析结果降级返回'
  if (source === 'FALLBACK') return '系统降级返回'
  return '回答来源未知'
}
</script>

<style scoped>
.answer-source-alert { margin-bottom: 10px; }
.notice { margin-top: 4px; line-height: 1.5; }
.meta-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
```

- [ ] **Step 2: Update `AiTraceButton.vue` wording and payload**

Use this structure:

```vue
<template>
  <el-button v-if="enabled" size="small" plain @click="openTrace">
    AI 执行过程
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  trace?: Record<string, any> | null
  execution?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (e: 'open', value: Record<string, any>): void
}>()

const enabled = computed(() => Boolean(props.execution || props.trace?.traceId || props.trace?.trace_id || props.trace?.steps))

function openTrace() {
  emit('open', props.execution || { trace: props.trace })
}
</script>
```

- [ ] **Step 3: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue srmp-web-ui/src/views/agent/components/AiTraceButton.vue
git commit -m "feat: show AI answer source metadata"
```

## Task 3: Rebuild the Unified AI Trace Drawer

**Files:**
- Modify: `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Replace trace-only props with execution props**

In `<script setup>`, import the normalizer and define props:

```ts
import { computed } from 'vue'
import AnswerSourceAlert from './AnswerSourceAlert.vue'
import { toAiExecutionSnapshot } from './aiExecution'

const props = defineProps<{
  visible: boolean
  trace?: Record<string, any> | null
  answerMeta?: Record<string, any> | null
  toolResults?: any[] | null
  sources?: any[] | null
  record?: Record<string, any> | null
  replayResult?: Record<string, any> | null
  solution?: Record<string, any> | null
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const snapshot = computed(() => toAiExecutionSnapshot({
  trace: props.trace,
  answerMeta: props.answerMeta,
  toolResults: props.toolResults,
  sources: props.sources,
  record: props.record,
  replayResult: props.replayResult,
  solution: props.solution
}))
```

- [ ] **Step 2: Replace the template with the unified drawer layout**

Use these sections:

```vue
<el-drawer
  :model-value="visible"
  title="AI 执行过程"
  size="640px"
  @update:model-value="$emit('update:visible', $event)"
>
  <el-empty v-if="!snapshot" description="暂无 AI 执行过程" />
  <template v-else>
    <el-descriptions :column="2" border size="small" class="trace-meta">
      <el-descriptions-item label="Provider">{{ snapshot.summary.provider || '-' }}</el-descriptions-item>
      <el-descriptions-item label="意图">{{ snapshot.summary.intent || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ snapshot.summary.status || '-' }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ snapshot.summary.costMs || 0 }} ms</el-descriptions-item>
      <el-descriptions-item label="工具">{{ snapshot.summary.toolSuccessCount || 0 }}/{{ snapshot.summary.toolTotalCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ snapshot.summary.sourceCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="TraceId">{{ snapshot.summary.traceId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="RecordId">{{ snapshot.summary.recordId || '-' }}</el-descriptions-item>
    </el-descriptions>

    <AnswerSourceAlert :meta="snapshot.answerMeta" allow-empty />

    <el-alert
      v-for="warning in snapshot.warnings"
      :key="warning"
      class="trace-warning"
      type="warning"
      show-icon
      :closable="false"
      :title="warning"
    />

    <section class="trace-section">
      <h3>执行时间线</h3>
      <el-timeline>
        <el-timeline-item
          v-for="step in snapshot.steps"
          :key="step.key"
          :type="timelineType(step.status)"
          :timestamp="`${step.costMs || 0}ms`"
        >
          <div class="step-title">
            <strong>{{ step.label }}</strong>
            <el-tag size="small" :type="tagType(step.status)">{{ step.status }}</el-tag>
          </div>
          <div class="step-meta">count={{ step.count ?? '-' }}</div>
          <div v-if="step.error" class="error">{{ step.error }}</div>
          <el-collapse v-if="step.data && Object.keys(step.data).length" class="detail-collapse">
            <el-collapse-item title="步骤数据" :name="step.key">
              <pre>{{ formatJson(step.data) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-timeline-item>
      </el-timeline>
    </section>

    <section class="trace-section">
      <h3>工具与证据</h3>
      <el-empty v-if="snapshot.tools.length === 0 && snapshot.evidence.sources.length === 0" description="暂无工具或来源" />
      <div v-for="tool in snapshot.tools" :key="tool.name" class="tool-row">
        <span>{{ tool.name }}</span>
        <el-tag size="small" :type="tool.success ? 'success' : 'danger'">{{ tool.success ? 'SUCCESS' : 'FAILED' }}</el-tag>
        <span class="muted">count={{ tool.count ?? '-' }}</span>
        <span v-if="tool.error" class="error">{{ tool.error }}</span>
      </div>
      <div v-if="snapshot.evidence.sources.length" class="source-summary">
        来源 {{ snapshot.evidence.sources.length }} 条；知识库 {{ snapshot.evidence.knowledgeCount || 0 }}；业务 {{ snapshot.evidence.businessCount || 0 }}；Outline {{ snapshot.evidence.outlineCount || 0 }}
      </div>
    </section>

    <section v-if="snapshot.quality && Object.keys(snapshot.quality).length" class="trace-section">
      <h3>质量保护</h3>
      <pre>{{ formatJson(snapshot.quality) }}</pre>
    </section>

    <el-collapse class="trace-section">
      <el-collapse-item title="原始诊断 JSON" name="raw">
        <pre>{{ formatJson(snapshot.raw) }}</pre>
      </el-collapse-item>
    </el-collapse>
  </template>
</el-drawer>
```

- [ ] **Step 3: Keep status helper functions and JSON formatting**

```ts
function tagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

function timelineType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'info'
}

function formatJson(value: any) {
  return JSON.stringify(value || {}, null, 2)
}
```

- [ ] **Step 4: Add drawer styles**

```css
.trace-meta { margin-bottom: 16px; }
.trace-warning { margin-bottom: 10px; }
.trace-section { margin-top: 18px; }
.trace-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
}
.step-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.step-meta,
.muted {
  color: #64748b;
  font-size: 12px;
}
.error {
  margin-top: 6px;
  color: #dc2626;
  word-break: break-all;
}
.detail-collapse { margin-top: 6px; }
.tool-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #eef2f7;
}
.source-summary {
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
}
pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-size: 12px;
}
```

- [ ] **Step 5: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 6: Commit**

```bash
git add srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue
git commit -m "feat: unify AI execution drawer"
```

## Task 4: Wire Ordinary AI Chat Page

**Files:**
- Modify: `srmp-web-ui/src/views/agent/AiChatPage.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Extend the message type**

Update `ChatMessage`:

```ts
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  trace?: Record<string, any> | null
  answerMeta?: Record<string, any> | null
  toolResults?: any[]
  sources?: any[]
}
```

- [ ] **Step 2: Pass full context to the trace button**

In the assistant message template, replace the trace button call:

```vue
<AiTraceButton
  v-if="item.role === 'assistant'"
  :trace="item.trace"
  :execution="{ trace: item.trace, answerMeta: item.answerMeta, toolResults: item.toolResults, sources: item.sources }"
  class="trace-button"
  @open="openTrace"
/>
```

- [ ] **Step 3: Store answer metadata and evidence when sending**

In `send()`, replace assistant push with:

```ts
const data = result?.data || {}
const sources = data.sources || data.knowledgeSources || result?.sources || []
messages.value.push({
  role: 'assistant',
  content: result?.answer || data.answer || JSON.stringify(result),
  trace: data.trace || result?.trace || null,
  answerMeta: data.answerMeta || result?.answerMeta || null,
  toolResults: data.toolResults || result?.toolResults || [],
  sources
})

knowledgeSources.value = data.knowledgeSources || data.sources || []
outlineSources.value = data.outlineSources || []
```

- [ ] **Step 4: Update active trace state to hold execution payloads**

Replace `activeTrace` and `openTrace`:

```ts
const activeExecution = ref<Record<string, any> | null>(null)

function openTrace(execution: Record<string, any>) {
  activeExecution.value = execution
  traceDrawerVisible.value = true
}
```

Then replace the drawer:

```vue
<AiTraceDrawer
  v-model:visible="traceDrawerVisible"
  :trace="activeExecution?.trace || null"
  :answer-meta="activeExecution?.answerMeta || null"
  :tool-results="activeExecution?.toolResults || []"
  :sources="activeExecution?.sources || []"
/>
```

- [ ] **Step 5: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 6: Commit**

```bash
git add srmp-web-ui/src/views/agent/AiChatPage.vue
git commit -m "feat: expose AI execution in chat page"
```

## Task 5: Wire GIS Chat Float and Solution Preview

**Files:**
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Modify: `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Add assistant message tags in GIS chat**

In `AgentChatFloat.vue`, add tags next to the existing answer source tags:

```vue
<el-tag v-if="item.meta?.intent" size="small" type="info">{{ item.meta.intent }}</el-tag>
<el-tag v-if="item.toolResults?.length" size="small" type="info">工具 {{ successfulTools(item.toolResults) }}/{{ item.toolResults.length }}</el-tag>
<el-tag v-if="item.sources?.length" size="small" type="info">来源 {{ item.sources.length }}</el-tag>
<el-tag v-if="item.meta?.retriedWithCompactPrompt" size="small" type="warning">压缩重试</el-tag>
```

Add helper:

```ts
function successfulTools(tools: any[] = []) {
  return tools.filter((item) => item?.success !== false && String(item?.status || '').toUpperCase() !== 'FAILED').length
}
```

- [ ] **Step 2: Pass full execution payload to GIS trace button**

Replace GIS trace button:

```vue
<AiTraceButton
  v-if="item.role === 'assistant'"
  :trace="item.trace"
  :execution="{ trace: item.trace, answerMeta: item.meta, toolResults: item.toolResults, sources: item.sources }"
  class="trace-button"
  @open="openTrace"
/>
```

- [ ] **Step 3: Preserve intent and LLM metadata in assistant message meta**

In the assistant message push, extend `meta`:

```ts
meta: {
  ...meta,
  intent: payload.data?.intent || payload.intent,
  llmStatus: meta?.llmStatus || payload.data?.llmStatus,
  llmModel: meta?.llmModel || payload.data?.llmModel,
  retriedWithCompactPrompt: meta?.retriedWithCompactPrompt || payload.data?.retriedWithCompactPrompt,
  mapObjectUsed: payload.data?.mapObjectUsed || payload.mapObjectUsed || meta?.mapObjectUsed,
  regionUsed: payload.data?.regionUsed || payload.data?.mapRegionUsed || payload.mapRegionUsed || meta?.regionUsed
}
```

- [ ] **Step 4: Update GIS active trace state**

Replace `activeTrace` usage with `activeExecution`:

```ts
const activeExecution = ref<Record<string, any> | null>(null)

function openTrace(execution: Record<string, any>) {
  activeExecution.value = execution
  traceDrawerVisible.value = true
}
```

Use drawer:

```vue
<AiTraceDrawer
  v-model:visible="traceDrawerVisible"
  :trace="activeExecution?.trace || null"
  :answer-meta="activeExecution?.answerMeta || null"
  :tool-results="activeExecution?.toolResults || []"
  :sources="activeExecution?.sources || []"
/>
```

- [ ] **Step 5: Pass solution metadata in `SolutionPreviewDialog.vue`**

Replace footer button:

```vue
<AiTraceButton
  :trace="activeTrace"
  :execution="{ trace: activeTrace, answerMeta, toolResults, sources, solution }"
  @open="openTrace"
/>
```

Add computed values:

```ts
const answerMeta = computed(() => (props.solution as any)?.answerMeta || (props.solution as any)?.answer_meta || null)
const toolResults = computed(() => (props.solution as any)?.toolResults || [])
const sources = computed(() => (props.solution as any)?.sources || (props.solution as any)?.knowledgeSources || [])
const activeExecution = ref<Record<string, any> | null>(null)

function openTrace(execution: Record<string, any>) {
  activeExecution.value = execution
  traceDrawerVisible.value = true
}
```

Replace drawer:

```vue
<AiTraceDrawer
  v-model:visible="traceDrawerVisible"
  :trace="activeExecution?.trace || activeTrace"
  :answer-meta="activeExecution?.answerMeta || answerMeta"
  :tool-results="activeExecution?.toolResults || toolResults"
  :sources="activeExecution?.sources || sources"
  :solution="activeExecution?.solution || solution"
/>
```

- [ ] **Step 6: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 7: Commit**

```bash
git add srmp-web-ui/src/views/gis/components/AgentChatFloat.vue srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue
git commit -m "feat: expose AI execution in GIS chat"
```

## Task 6: Wire Region Solution Trace in OneMap

**Files:**
- Modify: `srmp-web-ui/src/views/gis/OneMap.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Pass region solution metadata to drawer**

Replace the region drawer usage:

```vue
<AiTraceDrawer
  v-model:visible="regionTraceDrawerVisible"
  :trace="regionSolution?.trace || null"
  :answer-meta="regionSolution?.answerMeta || null"
  :tool-results="regionSolution?.toolResults || []"
  :sources="regionSolution?.sources || regionSolution?.knowledgeSources || []"
  :solution="regionSolution || null"
/>
```

- [ ] **Step 2: Ensure generated region solution keeps sources and tool results**

In `generateRegionSolution()`, after normalizing the response, preserve fields with this shape if they are missing:

```ts
regionSolution.value = {
  ...result,
  answerMeta: result.answerMeta || result.data?.answerMeta || null,
  toolResults: result.toolResults || result.data?.toolResults || [],
  sources: result.sources || result.knowledgeSources || result.data?.sources || []
}
```

- [ ] **Step 3: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add srmp-web-ui/src/views/gis/OneMap.vue
git commit -m "feat: enrich region AI execution trace"
```

## Task 7: Upgrade LangGraph Ops Runtime Record and Replay View

**Files:**
- Modify: `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Import the shared drawer**

Add import:

```ts
import AiTraceDrawer from './components/AiTraceDrawer.vue'
```

- [ ] **Step 2: Add execution drawer state**

Add state near existing dialog refs:

```ts
const executionDrawerVisible = ref(false)
const selectedExecutionRecord = ref<Record<string, any> | null>(null)
const selectedReplayResult = ref<Record<string, any> | null>(null)
const replayCompare = ref<Record<string, any> | null>(null)
```

- [ ] **Step 3: Add drawer and replay comparison to the template**

Add after the existing JSON dialog:

```vue
<AiTraceDrawer
  v-model:visible="executionDrawerVisible"
  :record="selectedExecutionRecord"
  :replay-result="selectedReplayResult"
/>

<el-card v-if="replayCompare" class="result-card">
  <template #header>回放对比</template>
  <el-descriptions :column="3" border size="small">
    <el-descriptions-item label="原始状态">{{ replayCompare.originalStatus || '-' }}</el-descriptions-item>
    <el-descriptions-item label="回放状态">{{ replayCompare.replayStatus || '-' }}</el-descriptions-item>
    <el-descriptions-item label="执行模式">{{ replayCompare.execute ? '执行回放' : 'Plan 回放' }}</el-descriptions-item>
    <el-descriptions-item label="原始意图">{{ replayCompare.originalIntent || '-' }}</el-descriptions-item>
    <el-descriptions-item label="回放意图">{{ replayCompare.replayIntent || '-' }}</el-descriptions-item>
    <el-descriptions-item label="工具">{{ replayCompare.toolText || '-' }}</el-descriptions-item>
  </el-descriptions>
</el-card>
```

- [ ] **Step 4: Change Recent actions wording**

Use:

```vue
<el-button size="small" text @click="openRecord(scope.row)">执行过程</el-button>
<el-button size="small" text :loading="replaying" @click="replayRecord(scope.row, false)">Plan回放</el-button>
<el-button size="small" text type="warning" :loading="replaying" @click="replayRecord(scope.row, true)">执行回放</el-button>
```

- [ ] **Step 5: Open record in the shared drawer**

Replace `openRecord()` with:

```ts
async function openRecord(row: Record<string, any>) {
  selectedRecordText.value = JSON.stringify(row, null, 2)
  selectedExecutionRecord.value = row
  selectedReplayResult.value = null
  executionDrawerVisible.value = true
  const id = row?.id || row?.traceId
  if (!id) return
  try {
    const res = await getOrchestratorRecord(String(id))
    selectedRecordText.value = JSON.stringify(res, null, 2)
    selectedExecutionRecord.value = value(res, ['body']) || value(res, ['data']) || res
  } catch (error: any) {
    selectedRecordText.value = JSON.stringify({ row, detailError: error?.response?.data || error?.message || error }, null, 2)
  }
}
```

- [ ] **Step 6: Capture replay comparison**

In `replayRecord()`, after success response:

```ts
const body = value(res, ['body']) || value(res, ['data']) || res
selectedReplayResult.value = body
selectedExecutionRecord.value = row
executionDrawerVisible.value = true
replayCompare.value = buildReplayCompare(row, body, execute)
```

Add helper:

```ts
function buildReplayCompare(original: Record<string, any>, replay: Record<string, any>, execute: boolean) {
  const response = value(replay, ['response']) || replay
  const data = value(response, ['data']) || {}
  return {
    execute,
    originalStatus: original.status,
    replayStatus: value(replay, ['status']) || value(response, ['status']) || 'SUCCESS',
    originalIntent: original.intent,
    replayIntent: data.intent || value(response, ['intent']) || value(replay, ['intent']),
    toolText: `${data.toolSuccessCount ?? original.toolSuccessCount ?? 0}/${data.toolTotalCount ?? original.toolTotalCount ?? 0}`
  }
}
```

- [ ] **Step 7: Keep raw JSON as advanced diagnostics**

Do not remove `recordDialogVisible` or `selectedRecordText`. `exportDiagnostics()` should continue to open the JSON dialog and copy the payload.

- [ ] **Step 8: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git add srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
git commit -m "feat: show LangGraph runtime execution details"
```

## Task 8: Optional Runtime Audit Summary Fields

**Files:**
- Modify if needed: `srmp-ai-orchestrator/app/observability.py`
- Test if modified: `srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py`

- [ ] **Step 1: Decide if this task is needed**

After Task 7, inspect Ops recent rows. If the drawer can derive answer metadata from `responsePreview.data.answerMeta`, skip this task and make no commit.

- [ ] **Step 2: If needed, add safe top-level fields**

Inside `RuntimeAuditStore.record_success()`, after `data = response.data or {}`, add:

```python
answer_meta = data.get("answerMeta") if isinstance(data, dict) else {}
quality = data.get("quality") if isinstance(data, dict) else {}
```

Then add these keys to `record`:

```python
"answerSource": answer_meta.get("answerSource") if isinstance(answer_meta, dict) else None,
"llmSuccess": answer_meta.get("llmSuccess") if isinstance(answer_meta, dict) else None,
"llmStatus": answer_meta.get("llmStatus") if isinstance(answer_meta, dict) else None,
"llmModel": answer_meta.get("llmModel") if isinstance(answer_meta, dict) else None,
"qualityFallback": quality.get("fallbackApplied") if isinstance(quality, dict) else None,
```

- [ ] **Step 3: Run Python compile**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
```

Expected: command exits with code 0 and no syntax errors.

- [ ] **Step 4: Commit only if modified**

```bash
git add srmp-ai-orchestrator/app/observability.py
git commit -m "feat: expose safe LangGraph audit metadata"
```

## Task 9: Add Phase50.17 Verification Script

**Files:**
- Create: `scripts/check-phase50-17-langgraph-explainability.sh`
- Test: `bash scripts/check-phase50-17-langgraph-explainability.sh`

- [ ] **Step 1: Create the verification script**

Use:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message"
    echo "       file: $file"
    echo "       pattern: $pattern"
    exit 1
  fi
  echo "[OK] $message"
}

require_pattern "srmp-web-ui/src/views/agent/components/aiExecution.ts" "export interface AiExecutionSnapshot" "execution snapshot model exists"
require_pattern "srmp-web-ui/src/views/agent/components/aiExecution.ts" "toAiExecutionSnapshot" "execution normalizer exists"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "AI 执行过程" "drawer uses unified title"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "AnswerSourceAlert" "drawer renders answer source summary"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "工具与证据" "drawer renders tools and evidence"
require_pattern "srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue" "原始诊断 JSON" "drawer keeps raw diagnostics"
require_pattern "srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue" "llmModel" "answer source shows LLM model"
require_pattern "srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue" "retriedWithCompactPrompt" "answer source shows retry metadata"
require_pattern "srmp-web-ui/src/views/agent/AiChatPage.vue" "answerMeta" "ordinary chat stores answerMeta"
require_pattern "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue" "toolResults" "GIS chat passes tool results"
require_pattern "srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue" "answerMeta" "solution preview passes answerMeta"
require_pattern "srmp-web-ui/src/views/gis/OneMap.vue" "regionSolution\\?\\.answerMeta" "region solution passes answerMeta"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "executionDrawerVisible" "Ops page has execution drawer state"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "buildReplayCompare" "Ops page has replay comparison"

npm --prefix srmp-web-ui run build

echo "[OK] Phase50.17 LangGraph explainability checks passed"
```

- [ ] **Step 2: Make the script executable**

Run:

```bash
chmod +x scripts/check-phase50-17-langgraph-explainability.sh
```

- [ ] **Step 3: Run the script**

Run:

```bash
bash scripts/check-phase50-17-langgraph-explainability.sh
```

Expected:

```text
[OK] execution snapshot model exists
...
[OK] Phase50.17 LangGraph explainability checks passed
```

- [ ] **Step 4: Commit**

```bash
git add scripts/check-phase50-17-langgraph-explainability.sh
git commit -m "test: add LangGraph explainability checks"
```

## Task 10: Full Regression and Final Integration

**Files:**
- No new files expected.
- Test: existing Phase50 regression scripts and build.

- [ ] **Step 1: Run Phase50.17 verification**

```bash
bash scripts/check-phase50-17-langgraph-explainability.sh
```

Expected: all checks pass.

- [ ] **Step 2: Run existing LangGraph parity regressions**

```bash
bash scripts/check-phase50-14-langgraph-map-agent-parity.sh
bash scripts/check-phase50-15-langgraph-native-parity.sh
bash scripts/check-phase50-16-langgraph-llm-live-closure.sh
```

Expected: each script exits with code 0.

- [ ] **Step 3: Compile orchestrator Python if Task 8 changed Python**

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
```

Expected: command exits with code 0.

- [ ] **Step 4: Run frontend build one final time**

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 5: Inspect git status**

```bash
git status --short
```

Expected: only unrelated untracked runtime files may remain:

```text
?? deploy/outline/data/
?? deploy/outline/outline.env
?? deploy/outline/outline.env.bak
```

- [ ] **Step 6: Create final integration commit if any uncommitted implementation changes remain**

```bash
git add srmp-web-ui/src/views/agent/components/aiExecution.ts \
  srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue \
  srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue \
  srmp-web-ui/src/views/agent/components/AiTraceButton.vue \
  srmp-web-ui/src/views/agent/AiChatPage.vue \
  srmp-web-ui/src/views/gis/components/AgentChatFloat.vue \
  srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue \
  srmp-web-ui/src/views/gis/OneMap.vue \
  srmp-web-ui/src/views/agent/LangGraphOpsPage.vue \
  scripts/check-phase50-17-langgraph-explainability.sh
git commit -m "feat: expose LangGraph execution explainability"
```

Expected: commit succeeds or reports there is nothing to commit because earlier task commits already captured all changes.

## Manual Acceptance Checklist

- [ ] `/agent/chat` or the普通 AI 问答页 generates an assistant response with an `AI 执行过程` button.
- [ ] `/gis/one-map` 一张图浮窗回答 shows intent/tool/source/fallback tags and opens the same drawer.
- [ ] 单对象方案预览 opens the same drawer and shows answer source, timeline, tools/evidence, raw diagnostics.
- [ ] 区域养护建议 opens the same drawer and shows region analysis/generation/quality steps when present.
- [ ] LangGraph Ops Recent row opens a readable execution drawer.
- [ ] Ops Plan 回放 and 执行回放 produce a comparison card.
- [ ] Raw JSON is still available for diagnostics.
- [ ] No API keys, Authorization headers, cookies, or provider secrets are displayed.
