# AI Trace Diagnosis Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared AI execution diagnosis layer so `/agent/ai-traces` and the AI execution drawer show clear administrator-facing conclusions before raw logs.

**Architecture:** Extend the existing `toAiExecutionSnapshot` frontend normalizer with a derived `diagnosis` object. Render that same object in `AiTracesPage.vue` and `AiTraceDrawer.vue`, keeping raw steps, tools, evidence, policy checks, and JSON below the summary.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Node built-in test runner, Vite.

---

## File Structure

- Modify `srmp-web-ui/src/views/agent/components/aiExecution.ts`
  - Owns the diagnosis data model and priority rules.
  - Keeps diagnosis pure and derived from existing snapshot inputs.
- Modify `srmp-web-ui/src/views/agent/AiTracesPage.vue`
  - Renders the diagnosis block at the top of the selected trace overview.
- Modify `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
  - Renders the same diagnosis block near the top of the drawer.
- Modify `srmp-web-ui/tests/liveTrace.test.mjs`
  - Adds behavior tests for diagnosis generation.
- Modify `srmp-web-ui/tests/aiTracesPage.test.mjs`
  - Adds structure tests for the page and drawer rendering.

## Task 1: Define Diagnosis Behavior With Failing Tests

**Files:**
- Modify: `srmp-web-ui/tests/liveTrace.test.mjs`

- [ ] **Step 1: Add tests for diagnosis rules**

Append these tests after the existing AI trace snapshot tests:

```js
test('AiTrace diagnosis explains no embedded chunks as vector readiness issue', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-diag-vector',
      status: 'SUCCESS',
      steps: [
        {
          step_name: 'tool_execute',
          step_label: '执行只读工具',
          status: 'SUCCESS',
          step_data: {
            toolResults: [
              {
                toolName: 'knowledge.retrieve',
                success: true,
                data: { fallbackReason: 'no embedded chunks' }
              }
            ]
          }
        }
      ]
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true }
  })

  assert.equal(snapshot.diagnosis.severity, 'warning')
  assert.equal(snapshot.diagnosis.title, '知识库向量未就绪')
  assert.match(snapshot.diagnosis.summary, /向量/)
  assert.match(snapshot.diagnosis.cause, /no embedded chunks/)
  assert.equal(snapshot.diagnosis.actions[0].key, 'VECTORIZE_OUTLINE')
  assert.deepEqual(snapshot.diagnosis.tags.map((item) => item.label), ['模型', '降级', '工具', '证据'])
})

test('AiTrace diagnosis treats completed missing answerMeta as metadata gap', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-diag-no-meta',
      status: 'SUCCESS',
      steps: [{ name: 'answer_generate', label: '生成回答', status: 'SUCCESS' }]
    }
  })

  assert.equal(snapshot.diagnosis.severity, 'info')
  assert.equal(snapshot.diagnosis.title, '模型来源元数据缺失')
  assert.match(snapshot.diagnosis.summary, /旧记录|旧接口|LangGraph/)
  assert.match(snapshot.diagnosis.cause, /answerMeta/)
})

test('AiTrace diagnosis suppresses final metadata gap while execution is running', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-diag-running',
      status: 'RUNNING',
      currentStep: { name: 'answer_generate', label: '生成回答', status: 'RUNNING', elapsedMs: 1200 }
    }
  })

  assert.equal(snapshot.diagnosis.severity, 'info')
  assert.equal(snapshot.diagnosis.title, '执行中')
  assert.match(snapshot.diagnosis.summary, /生成回答/)
  assert.doesNotMatch(snapshot.diagnosis.summary, /answerMeta/)
})

test('AiTrace diagnosis prioritizes failed tools over generic evidence warnings', () => {
  const snapshot = toAiExecutionSnapshot({
    record: {
      traceId: 'trace-diag-tool-failed',
      status: 'FAILED',
      toolResults: [
        { toolName: 'gis.queryDiseases', success: false, error: 'database timeout' },
        { toolName: 'knowledge.retrieve', success: true }
      ]
    },
    answerMeta: { llmStatus: 'SKIPPED', llmSuccess: false }
  })

  assert.equal(snapshot.diagnosis.severity, 'danger')
  assert.equal(snapshot.diagnosis.title, '工具调用失败')
  assert.match(snapshot.diagnosis.summary, /gis\.queryDiseases/)
  assert.match(snapshot.diagnosis.cause, /database timeout/)
})

test('AiTrace diagnosis surfaces business evidence gaps', () => {
  const snapshot = toAiExecutionSnapshot({
    record: {
      traceId: 'trace-diag-business-gap',
      status: 'SUCCESS',
      capability: {
        capabilityId: 'map.route_analysis',
        contextUsage: 'BUSINESS_FIRST'
      },
      toolResults: [
        { toolName: 'gis.queryAssessmentResults', success: true, count: 0 },
        { toolName: 'knowledge.retrieve', success: true, count: 2 }
      ],
      sourceCount: 2
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true }
  })

  assert.equal(snapshot.diagnosis.severity, 'warning')
  assert.equal(snapshot.diagnosis.title, '业务证据不足')
  assert.match(snapshot.diagnosis.summary, /GIS|业务/)
})

test('AiTrace diagnosis surfaces policy or plan divergence', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-diag-plan',
      status: 'SUCCESS',
      planExecution: {
        available: true,
        status: 'PARTIAL',
        plannedToolNames: ['gis.queryAssessmentResults', 'gis.queryDiseases'],
        actualToolNames: ['gis.queryAssessmentResults'],
        missingToolNames: ['gis.queryDiseases'],
        extraToolNames: []
      }
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true }
  })

  assert.equal(snapshot.diagnosis.severity, 'warning')
  assert.equal(snapshot.diagnosis.title, '执行计划存在偏差')
  assert.match(snapshot.diagnosis.summary, /gis\.queryDiseases/)
})

test('AiTrace diagnosis marks healthy execution when model tools and evidence are present', () => {
  const snapshot = toAiExecutionSnapshot({
    record: {
      traceId: 'trace-diag-healthy',
      status: 'SUCCESS',
      toolResults: [
        { toolName: 'gis.queryDiseases', success: true, count: 8 },
        { toolName: 'knowledge.retrieve', success: true, count: 2 }
      ],
      sources: [
        { sourceType: 'BUSINESS_DATA', title: '病害统计' },
        { sourceType: 'KNOWLEDGE', title: '养护规范' }
      ],
      sourceCount: 2
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true, answerSource: 'LLM' }
  })

  assert.equal(snapshot.diagnosis.severity, 'success')
  assert.equal(snapshot.diagnosis.title, '执行证据完整')
  assert.match(snapshot.diagnosis.summary, /模型、工具和证据/)
})
```

- [ ] **Step 2: Run the failing diagnosis tests**

Run:

```bash
cd /Users/cxp/workspace/srmp/srmp-web-ui
node --import ./tests/ts-loader.mjs --test tests/liveTrace.test.mjs
```

Expected: FAIL with assertions or `snapshot.diagnosis` undefined.

## Task 2: Implement the Shared Diagnosis Model

**Files:**
- Modify: `srmp-web-ui/src/views/agent/components/aiExecution.ts`
- Test: `srmp-web-ui/tests/liveTrace.test.mjs`

- [ ] **Step 1: Add diagnosis interfaces**

Insert these interfaces after `AiExecutionRepairAction`:

```ts
export type AiExecutionDiagnosisSeverity = 'success' | 'info' | 'warning' | 'danger'

export interface AiExecutionDiagnosisTag {
  label: string
  value: string
  type?: AiExecutionDiagnosisSeverity
}

export interface AiExecutionDiagnosis {
  severity: AiExecutionDiagnosisSeverity
  title: string
  summary: string
  cause: string
  actions: AiExecutionRepairAction[]
  tags: AiExecutionDiagnosisTag[]
}
```

Add this field to `AiExecutionSnapshot`:

```ts
diagnosis: AiExecutionDiagnosis
```

- [ ] **Step 2: Build repair actions before returning the snapshot**

In `toAiExecutionSnapshot`, replace the direct `return { ... }` block with a local `repairActions` variable and use it in both `diagnosis` and `repairActions`:

```ts
  const warnings = buildWarnings(answerMeta, steps, summary.sourceCount, sources.length, tools, summary.status, currentStep, policyChecks)
  const repairActions = buildRepairActions(tools, answerMeta)
  const evidence = {
    sourceCount: summary.sourceCount,
    knowledgeCount: numberValue(liveSourceSummary.knowledge) ?? evidenceData.knowledgeHitCount ?? countSources(sources, 'KNOWLEDGE'),
    businessCount: numberValue(liveSourceSummary.business) ?? evidenceData.businessHitCount ?? countSources(sources, 'BUSINESS'),
    outlineCount: numberValue(liveSourceSummary.outline) ?? evidenceData.outlineHitCount ?? countSources(sources, 'OUTLINE'),
    sources
  }

  return {
    summary,
    answerMeta,
    currentStep,
    steps,
    tools,
    evidence,
    quality,
    businessScope,
    capability,
    planExecution,
    policyStatus,
    policyChecks,
    raw: sanitizeInternalDiagnostics(compactRaw(input)) as Record<string, any>,
    warnings,
    repairActions,
    diagnosis: buildDiagnosis({
      summary,
      answerMeta,
      currentStep,
      tools,
      evidence,
      capability,
      planExecution,
      policyChecks,
      repairActions
    })
  }
```

- [ ] **Step 3: Add the diagnosis builder**

Add this helper block below `buildWarnings`:

```ts
function buildDiagnosis(input: {
  summary: AiExecutionSnapshot['summary']
  answerMeta: Record<string, any>
  currentStep?: AiExecutionStep | null
  tools: AiExecutionTool[]
  evidence: AiExecutionSnapshot['evidence']
  capability: Record<string, any>
  planExecution: AiExecutionPlanExecution
  policyChecks: AiExecutionPolicyCheck[]
  repairActions: AiExecutionRepairAction[]
}): AiExecutionDiagnosis {
  const tags = buildDiagnosisTags(input.summary, input.answerMeta, input.tools, input.evidence)
  if (isExecutionInProgress(input.summary.status, input.currentStep)) {
    return {
      severity: 'info',
      title: '执行中',
      summary: input.currentStep?.label ? `当前正在${input.currentStep.label}。` : 'AI 执行仍在进行中，最终模型来源和证据结论尚未收口。',
      cause: input.currentStep?.status || input.summary.status || 'RUNNING',
      actions: [],
      tags
    }
  }

  const failedTools = input.tools.filter((tool) => !tool.success)
  if (failedTools.length) {
    const first = failedTools[0]
    return {
      severity: 'danger',
      title: '工具调用失败',
      summary: `共有 ${failedTools.length} 个工具失败，优先检查 ${first.name}。`,
      cause: stringValue(first.error, first.diagnostic, first.fallbackReason, '工具返回失败或异常') || '工具返回失败或异常',
      actions: input.repairActions,
      tags
    }
  }

  const knowledge = knowledgeDiagnosis(input.tools, input.answerMeta, input.repairActions, tags)
  if (knowledge) return knowledge

  if (hasBusinessEvidenceGap(input.capability, input.tools, input.evidence)) {
    return {
      severity: 'warning',
      title: '业务证据不足',
      summary: '当前能力需要 GIS 或业务数据支撑，但本次没有形成有效业务证据。',
      cause: '业务工具返回为空、未命中，或来源中缺少 BUSINESS_DATA。',
      actions: input.repairActions,
      tags
    }
  }

  const failedPolicyChecks = input.policyChecks.filter((item) => item.status === 'FAIL')
  if (failedPolicyChecks.length || input.planExecution.missingToolNames.length || input.planExecution.missingSourceTypes.length) {
    const missing = input.planExecution.missingToolNames.concat(input.planExecution.missingSourceTypes)
    return {
      severity: failedPolicyChecks.some((item) => String(item.severity).toUpperCase() === 'ERROR') ? 'danger' : 'warning',
      title: failedPolicyChecks.length ? '能力策略校验未通过' : '执行计划存在偏差',
      summary: failedPolicyChecks[0]?.message || (missing.length ? `缺少 ${missing.join(', ')}。` : '计划工具、实际工具或证据来源存在差异。'),
      cause: failedPolicyChecks[0]?.code || input.planExecution.status || 'PLAN_DIVERGENCE',
      actions: input.repairActions,
      tags
    }
  }

  if (!Object.keys(input.answerMeta).length) {
    return {
      severity: 'info',
      title: '模型来源元数据缺失',
      summary: '这条记录没有 answerMeta，通常来自旧记录、旧接口或未经过 LangGraph 管线的历史任务。',
      cause: 'answerMeta missing',
      actions: input.repairActions,
      tags
    }
  }

  return {
    severity: 'success',
    title: '执行证据完整',
    summary: '本次执行的模型、工具和证据链路已返回，可继续查看下方工具、证据和时间线细节。',
    cause: 'LLM、工具调用和证据来源均有可排查数据。',
    actions: input.repairActions,
    tags
  }
}
```

- [ ] **Step 4: Add diagnosis helpers**

Add these helpers below `buildDiagnosis`:

```ts
function buildDiagnosisTags(
  summary: AiExecutionSnapshot['summary'],
  answerMeta: Record<string, any>,
  tools: AiExecutionTool[],
  evidence: AiExecutionSnapshot['evidence']
): AiExecutionDiagnosisTag[] {
  const llmUsed = answerMeta.llmSuccess === true || String(answerMeta.answerSource || answerMeta.answer_source || '').toUpperCase() === 'LLM'
  return [
    { label: '模型', value: llmUsed ? '已使用' : (Object.keys(answerMeta).length ? '未成功' : '未知'), type: llmUsed ? 'success' : 'info' },
    { label: '降级', value: summary.fallback ? '是' : '否', type: summary.fallback ? 'warning' : 'success' },
    { label: '工具', value: `${summary.toolSuccessCount || 0}/${summary.toolTotalCount || tools.length || 0}`, type: (summary.toolFailedCount || tools.some((tool) => !tool.success)) ? 'danger' : 'success' },
    { label: '证据', value: String(evidence.sourceCount || evidence.sources.length || 0), type: (evidence.sourceCount || evidence.sources.length) ? 'success' : 'warning' }
  ]
}

function knowledgeDiagnosis(
  tools: AiExecutionTool[],
  answerMeta: Record<string, any>,
  repairActions: AiExecutionRepairAction[],
  tags: AiExecutionDiagnosisTag[]
): AiExecutionDiagnosis | null {
  const knowledgeTool = tools.find((tool) => tool.name === 'knowledge.retrieve' && (tool.fallbackReason || tool.diagnostic))
  const reason = stringValue(
    knowledgeTool?.fallbackReason,
    answerMeta.knowledgeFallbackReason,
    answerMeta.knowledge_fallback_reason,
    answerMeta.fallbackReason,
    answerMeta.fallback_reason
  )?.toLowerCase()
  if (!reason) return null

  if (reason === 'no embedded chunks') {
    return {
      severity: 'warning',
      title: '知识库向量未就绪',
      summary: '知识库有切片但没有可用向量，AI 已尝试关键词兜底，回答可能缺少语义检索证据。',
      cause: 'knowledge.retrieve fallbackReason: no embedded chunks',
      actions: repairActions,
      tags
    }
  }
  if (reason === 'no knowledge chunks') {
    return {
      severity: 'warning',
      title: '知识库暂无切片',
      summary: '本地知识库没有可检索切片，AI 只能依赖业务数据或模板兜底。',
      cause: 'knowledge.retrieve fallbackReason: no knowledge chunks',
      actions: repairActions,
      tags
    }
  }
  if (reason === 'query is empty') {
    return {
      severity: 'info',
      title: '知识检索词为空',
      summary: '本次问题没有形成有效知识库检索词，知识库未参与回答。',
      cause: 'knowledge.retrieve fallbackReason: query is empty',
      actions: repairActions,
      tags
    }
  }
  if (reason.includes('pgvector')) {
    return {
      severity: 'warning',
      title: '向量检索能力异常',
      summary: 'pgvector 或向量检索配置不可用，AI 已尝试关键词检索兜底。',
      cause: `knowledge.retrieve fallbackReason: ${reason}`,
      actions: repairActions,
      tags
    }
  }
  return null
}

function hasBusinessEvidenceGap(
  capability: Record<string, any>,
  tools: AiExecutionTool[],
  evidence: AiExecutionSnapshot['evidence']
): boolean {
  const capabilityText = JSON.stringify(capability || {}).toUpperCase()
  const expectsBusiness = capabilityText.includes('BUSINESS') || capabilityText.includes('MAP_') || capabilityText.includes('GIS')
  if (!expectsBusiness) return false
  const hasBusinessTool = tools.some((tool) => tool.name.startsWith('gis.') || tool.name.startsWith('solution.'))
  if (!hasBusinessTool) return false
  const businessCount = evidence.businessCount || 0
  const hasBusinessSource = businessCount > 0 || evidence.sources.some((source) => String(source.sourceType || source.source_type || source.type || '').toUpperCase().includes('BUSINESS'))
  const businessToolsHaveOnlyEmptyResults = tools
    .filter((tool) => tool.name.startsWith('gis.') || tool.name.startsWith('solution.'))
    .every((tool) => Number(tool.count || 0) === 0)
  return !hasBusinessSource && businessToolsHaveOnlyEmptyResults
}
```

- [ ] **Step 5: Run diagnosis tests**

Run:

```bash
cd /Users/cxp/workspace/srmp/srmp-web-ui
node --import ./tests/ts-loader.mjs --test tests/liveTrace.test.mjs
```

Expected: PASS.

## Task 3: Render Diagnosis in Trace Overview and Drawer

**Files:**
- Modify: `srmp-web-ui/src/views/agent/AiTracesPage.vue`
- Modify: `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
- Modify: `srmp-web-ui/tests/aiTracesPage.test.mjs`

- [ ] **Step 1: Add structure tests**

Append these tests to `srmp-web-ui/tests/aiTracesPage.test.mjs`:

```js
test('AI traces page renders shared diagnosis conclusion before detail sections', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /<section v-if="selectedExecutionSnapshot\?\.diagnosis" class="detail-section diagnosis-panel/)
  assert.match(content, /诊断结论/)
  assert.match(content, /selectedExecutionSnapshot\.diagnosis\.title/)
  assert.match(content, /selectedExecutionSnapshot\.diagnosis\.summary/)
  assert.match(content, /selectedExecutionSnapshot\.diagnosis\.cause/)
  assert.match(content, /selectedExecutionSnapshot\.diagnosis\.tags/)
  assert.match(content, /diagnosisAlertType/)
})

test('AI trace drawer renders shared diagnosis conclusion before timeline', () => {
  const content = read('src/views/agent/components/AiTraceDrawer.vue')

  assert.match(content, /<section v-if="snapshot\.diagnosis" class="trace-section diagnosis-panel/)
  assert.match(content, /诊断结论/)
  assert.match(content, /snapshot\.diagnosis\.title/)
  assert.match(content, /snapshot\.diagnosis\.summary/)
  assert.match(content, /snapshot\.diagnosis\.cause/)
  assert.match(content, /snapshot\.diagnosis\.tags/)
  assert.match(content, /diagnosisAlertType/)
})
```

- [ ] **Step 2: Run the failing structure tests**

Run:

```bash
cd /Users/cxp/workspace/srmp/srmp-web-ui
node --test tests/aiTracesPage.test.mjs
```

Expected: FAIL because the diagnosis UI is not rendered yet.

- [ ] **Step 3: Render diagnosis in `AiTracesPage.vue`**

Insert this block immediately before `<section class="summary-grid">`:

```vue
          <section v-if="selectedExecutionSnapshot?.diagnosis" class="detail-section diagnosis-panel">
            <div class="section-title">
              <h3>诊断结论</h3>
              <el-tag size="small" :type="diagnosisAlertType(selectedExecutionSnapshot.diagnosis.severity)">
                {{ selectedExecutionSnapshot.diagnosis.title }}
              </el-tag>
            </div>
            <el-alert
              :type="diagnosisAlertType(selectedExecutionSnapshot.diagnosis.severity)"
              show-icon
              :closable="false"
              :title="selectedExecutionSnapshot.diagnosis.summary"
              :description="selectedExecutionSnapshot.diagnosis.cause"
            />
            <div class="diagnosis-tags">
              <el-tag
                v-for="tag in selectedExecutionSnapshot.diagnosis.tags"
                :key="tag.label"
                size="small"
                :type="diagnosisAlertType(tag.type || 'info')"
                effect="plain"
              >
                {{ tag.label }}：{{ tag.value }}
              </el-tag>
            </div>
          </section>
```

Add this helper near the existing tag helpers:

```ts
function diagnosisAlertType(severity?: string) {
  if (severity === 'success') return 'success'
  if (severity === 'danger') return 'error'
  if (severity === 'warning') return 'warning'
  return 'info'
}
```

Add this CSS near other panel styles:

```css
.diagnosis-panel {
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.diagnosis-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
```

- [ ] **Step 4: Render diagnosis in `AiTraceDrawer.vue`**

Insert this block immediately after the `<AnswerSourceAlert ... />` line:

```vue
      <section v-if="snapshot.diagnosis" class="trace-section diagnosis-panel">
        <div class="step-title">
          <h3>诊断结论</h3>
          <el-tag size="small" :type="diagnosisAlertType(snapshot.diagnosis.severity)">
            {{ snapshot.diagnosis.title }}
          </el-tag>
        </div>
        <el-alert
          :type="diagnosisAlertType(snapshot.diagnosis.severity)"
          show-icon
          :closable="false"
          :title="snapshot.diagnosis.summary"
          :description="snapshot.diagnosis.cause"
        />
        <div class="diagnosis-tags">
          <el-tag
            v-for="tag in snapshot.diagnosis.tags"
            :key="tag.label"
            size="small"
            :type="diagnosisAlertType(tag.type || 'info')"
            effect="plain"
          >
            {{ tag.label }}：{{ tag.value }}
          </el-tag>
        </div>
      </section>
```

Add this helper near `policyCheckTagType`:

```ts
function diagnosisAlertType(severity?: string) {
  if (severity === 'success') return 'success'
  if (severity === 'danger') return 'error'
  if (severity === 'warning') return 'warning'
  return 'info'
}
```

Add this CSS near `.policy-panel`:

```css
.diagnosis-panel {
  padding: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.diagnosis-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
```

- [ ] **Step 5: Run structure tests**

Run:

```bash
cd /Users/cxp/workspace/srmp/srmp-web-ui
node --test tests/aiTracesPage.test.mjs
```

Expected: PASS.

## Task 4: Full Frontend Verification and Commit

**Files:**
- Verify: `srmp-web-ui/src/views/agent/components/aiExecution.ts`
- Verify: `srmp-web-ui/src/views/agent/AiTracesPage.vue`
- Verify: `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
- Verify: `srmp-web-ui/tests/liveTrace.test.mjs`
- Verify: `srmp-web-ui/tests/aiTracesPage.test.mjs`

- [ ] **Step 1: Run targeted tests**

Run:

```bash
cd /Users/cxp/workspace/srmp/srmp-web-ui
node --import ./tests/ts-loader.mjs --test tests/liveTrace.test.mjs
node --test tests/aiTracesPage.test.mjs
```

Expected: both commands PASS.

- [ ] **Step 2: Run frontend build**

Run:

```bash
cd /Users/cxp/workspace/srmp/srmp-web-ui
npm run build
```

Expected: `vue-tsc --noEmit` passes and Vite build completes.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
cd /Users/cxp/workspace/srmp
git diff -- srmp-web-ui/src/views/agent/components/aiExecution.ts srmp-web-ui/src/views/agent/AiTracesPage.vue srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue srmp-web-ui/tests/liveTrace.test.mjs srmp-web-ui/tests/aiTracesPage.test.mjs
```

Expected: diff only contains diagnosis model, diagnosis UI, and tests.

- [ ] **Step 4: Commit implementation**

Run:

```bash
cd /Users/cxp/workspace/srmp
git add srmp-web-ui/src/views/agent/components/aiExecution.ts srmp-web-ui/src/views/agent/AiTracesPage.vue srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue srmp-web-ui/tests/liveTrace.test.mjs srmp-web-ui/tests/aiTracesPage.test.mjs
git commit -m "Enhance AI trace diagnosis"
```

Expected: commit succeeds.

## Self-Review

- Spec coverage: the plan adds a shared diagnosis model, renders it in both trace overview and drawer, explains knowledge fallback, suppresses running missing-meta confusion, and keeps logs below the summary.
- Placeholder scan: no placeholder steps are used; every task includes exact file paths, code snippets, and commands.
- Type consistency: `AiExecutionDiagnosis`, `diagnosis`, `diagnosisAlertType`, `repairActions`, and `tags` are named consistently across tests, model, page, and drawer.
