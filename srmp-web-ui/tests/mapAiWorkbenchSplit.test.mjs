import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

test('AgentChatFloat delegates conversation surface to MapAiWorkbench', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /import MapAiWorkbench from '\.\/map-ai\/MapAiWorkbench\.vue'/)
  assert.match(content, /<MapAiWorkbench\b/)
  assert.doesNotMatch(content, /<div class="message-list">/)
  assert.doesNotMatch(content, /<MapAiActionResultPanel\b/)
  assert.doesNotMatch(content, /<MapAiSuggestedActions\b/)
})

test('MapAiWorkbench keeps assistant answers focused on the conversation surface', () => {
  const content = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')

  assert.match(content, /@locate-source="\$emit\('locate-source', \$event\)"/)
  assert.match(content, /@ask-with-source="\$emit\('ask-with-source', \$event\)"/)
  assert.doesNotMatch(content, /latest-suggested-actions|generate-default-solution|run-action|preview-plan/)
})

test('map AI workbench does not show a duplicate context breadcrumb in the conversation', () => {
  const content = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')

  assert.doesNotMatch(content, /<MapAiContextPanel\b/)
  assert.doesNotMatch(content, /import MapAiContextPanel/)
})

test('split map AI workbench is constrained inside fixed chat panel', () => {
  const floatContent = read('src/views/gis/components/AgentChatFloat.vue')
  const workbenchContent = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')
  const conversationContent = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(floatContent, /\.agent-chat-float\s*\{[\s\S]*position:\s*fixed;[\s\S]*overflow-x:\s*hidden;[\s\S]*overflow-y:\s*auto;/)
  assert.match(workbenchContent, /\.map-ai-workbench\s*\{[\s\S]*flex:\s*1\s+1\s+180px;[\s\S]*min-height:\s*180px;[\s\S]*overflow:\s*hidden;/)
  assert.match(conversationContent, /\.map-ai-conversation\s*\{[\s\S]*min-height:\s*0;/)
  assert.match(conversationContent, /\.conversation-message-list\s*\{[\s\S]*min-height:\s*0;/)
})

test('map AI conversation keeps input dock visible while messages scroll', () => {
  const conversationContent = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(conversationContent, /<div[^>]*class="conversation-message-list"[^>]*>[\s\S]*<slot name="message-tail" \/>[\s\S]*<\/div>\s*<div class="send-row">/)
  assert.match(conversationContent, /\.map-ai-conversation\s*\{[\s\S]*overflow:\s*hidden;/)
  assert.match(conversationContent, /\.conversation-message-list\s*\{[\s\S]*min-height:\s*0;/)
  assert.match(conversationContent, /\.send-row\s*\{[\s\S]*position:\s*sticky;[\s\S]*bottom:\s*0;[\s\S]*z-index:\s*2;/)
})

test('map AI result panels stay inside the scrollable conversation area', () => {
  const workbenchContent = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')

  assert.match(workbenchContent, /<MapAiConversation[\s\S]*>\s*<template #message-tail>[\s\S]*<MapAiActionResultPanel\b/)
  assert.match(workbenchContent, /<template #message-tail>[\s\S]*<MapAiActionResultPanel\b[\s\S]*<slot name="message-tail" \/>[\s\S]*<\/template>\s*<\/MapAiConversation>/)
  assert.doesNotMatch(workbenchContent, /<template #message-tail>[\s\S]*<MapAiSuggestedActions\b/)
})

test('map AI wait panel is rendered inside the scrollable conversation area', () => {
  const floatContent = read('src/views/gis/components/AgentChatFloat.vue')
  const workbenchContent = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')

  assert.match(workbenchContent, /<MapAiActionResultPanel\b[\s\S]*\/>\s*<slot name="message-tail" \/>/)
  assert.match(floatContent, /<MapAiWorkbench[\s\S]*>\s*<template #message-tail>[\s\S]*<section v-if="aiBusy" class="ai-wait-panel"/)
  assert.doesNotMatch(floatContent, /<\/MapAiWorkbench>\s*<section v-if="aiBusy" class="ai-wait-panel"/)
})

test('assistant answer does not render a follow-up action strip', () => {
  const workbenchContent = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')
  const conversationContent = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(workbenchContent, /:map-object="mapObject"/)
  assert.doesNotMatch(workbenchContent, /latestSuggestedActions|latest-suggested-actions|@run-action|@preview-plan|@generate-default-solution/)
  assert.doesNotMatch(conversationContent, /MapAiSuggestedActions|assistant-next-actions|后续操作/)
  assert.doesNotMatch(conversationContent, /hasAssistantNextActions|shouldShowAssistantActions|shouldShowObjectSolutionAction|visibleSuggestedActions|dedupeSuggestedActions/)
  assert.doesNotMatch(conversationContent, /class="assistant-action-row"/)
})

test('map AI conversation scrolls new action results into view', () => {
  const conversationContent = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(conversationContent, /<div ref="messageListRef" class="conversation-message-list">/)
  assert.match(conversationContent, /watch\(\s*\(\)\s*=>\s*\[props\.messages\.length,\s*props\.loading,\s*props\.solutionLoading\][\s\S]*scrollMessageListToBottom/)
  assert.match(conversationContent, /new MutationObserver\(\(\) => \{[\s\S]*scrollMessageListToBottom/)
  assert.match(conversationContent, /messageListRef\.value\.scrollTop\s*=\s*messageListRef\.value\.scrollHeight/)
})

test('map AI panel compacts nonessential analysis text on short viewports', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /@media\s*\(max-height:\s*700px\)\s*\{[\s\S]*\.analysis-summary,[\s\S]*\.analysis-action-hint\s*\{[\s\S]*display:\s*none;/)
  assert.match(content, /@media\s*\(max-height:\s*700px\)\s*\{[\s\S]*\.analysis-metrics\s*\{[\s\S]*display:\s*none;/)
})

test('map AI analysis is a default header panel before settings and diagnostics', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /import \{ ChatDotRound, MapLocation, Monitor, Setting \} from '@element-plus\/icons-vue'/)
  assert.match(content, /const showAnalysisPanel = ref\(true\)/)
  assert.match(content, /<strong>AI 养护助手<\/strong>[\s\S]*class="header-icon-btn analysis-icon-button"[\s\S]*aria-label="一张图分析"[\s\S]*@click="toggleAnalysisPanel"[\s\S]*class="header-icon-btn settings-icon-button"/)
  assert.match(content, /<el-icon><MapLocation \/><\/el-icon>/)
  assert.match(content, /<section v-if="showAnalysisPanel" class="analysis-workbench"/)
  assert.match(content, /function toggleAnalysisPanel\(\)[\s\S]*showToolsPanel\.value = false[\s\S]*showDiagnosticsPanel\.value = false/)
})

test('map AI diagnostics use a header icon and render full details without a second fold', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /import \{ ChatDotRound, MapLocation, Monitor, Setting \} from '@element-plus\/icons-vue'/)
  assert.match(content, /const showDiagnosticsPanel = ref\(false\)/)
  assert.match(content, /class="header-icon-btn diagnostics-icon-button"[\s\S]*aria-label="状态诊断"[\s\S]*@click="toggleDiagnosticsPanel"/)
  assert.match(content, /<el-icon><Monitor \/><\/el-icon>/)
  assert.match(content, /<section v-if="showDiagnosticsPanel" class="diagnostics-panel header-diagnostics-panel">/)
  assert.match(content, /class="diagnostics-summary"/)
  assert.match(content, /v-if="quickDiagnostics" class="diagnostics-grid"/)
  assert.doesNotMatch(content, /diagnosticsExpanded|收起详情/)
  assert.doesNotMatch(content, /v-if="quickDiagnostics && diagnosticsExpanded" class="diagnostics-grid"/)
})

test('map AI diagnostics stay outside the settings drawer', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /<section v-if="showToolsPanel" class="settings-panel header-settings-panel">[\s\S]*<div class="settings-head">[\s\S]*<strong>设置<\/strong>[\s\S]*<span>当前依据：\{\{ optionSummary \}\}<\/span>[\s\S]*<div class="settings-grid">[\s\S]*<el-checkbox v-model="useAgentTools">Agent工具<\/el-checkbox>[\s\S]*<\/section>\s*<section v-if="showDiagnosticsPanel" class="diagnostics-panel header-diagnostics-panel">/)
  assert.match(content, /<strong>系统状态<\/strong>/)
  assert.doesNotMatch(content, /<el-button size="small" plain :loading="diagnosticsLoading" @click="loadQuickDiagnostics">状态诊断<\/el-button>/)
  assert.doesNotMatch(content, /LangGraph 状态|Runtime UP|Tool OK|LLM 关闭/)
  assert.doesNotMatch(content, /<\/div>\s*<section v-if="quickDiagnostics \|\| diagnosticsError" class="diagnostics-panel">/)
})

test('map AI analysis header avoids redundant metric tag and keeps plan inline', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')
  const titleRow = content.match(/<div class="analysis-title-row">([\s\S]*?)<\/div>\s*<\/div>/)?.[0] || ''

  assert.doesNotMatch(titleRow, /analysis-title-actions|activeMetricMeta\.shortName|el-tag/)
  assert.doesNotMatch(titleRow, /综合\s*MQI|状态诊断|执行计划|el-button/)
  assert.match(content, /<el-button size="small" plain @click="previewCurrentPlan">查看执行计划<\/el-button>/)
})

test('map AI optional controls collapse data source settings by default', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /import \{ ChatDotRound, MapLocation, Monitor, Setting \} from '@element-plus\/icons-vue'/)
  assert.match(content, /<div class="title-main-row">[\s\S]*<strong>AI 养护助手<\/strong>[\s\S]*class="header-icon-btn settings-icon-button"[\s\S]*aria-label="设置"[\s\S]*@click="toggleSettingsPanel"/)
  assert.match(content, /<el-icon><Setting \/><\/el-icon>/)
  assert.match(content, /function toggleSettingsPanel\(\)[\s\S]*showAnalysisPanel\.value = false[\s\S]*showDiagnosticsPanel\.value = false/)
  assert.match(content, /async function toggleDiagnosticsPanel\(\)[\s\S]*showAnalysisPanel\.value = false[\s\S]*showToolsPanel\.value = false/)
  assert.match(content, /<section v-if="showToolsPanel" class="settings-panel header-settings-panel">[\s\S]*<div class="settings-head">[\s\S]*<strong>设置<\/strong>[\s\S]*<span>当前依据：\{\{ optionSummary \}\}<\/span>[\s\S]*<div class="settings-grid">/)
  assert.match(content, /\.settings-panel,\s*\.diagnostics-panel,[\s\S]*\.ai-wait-panel\s*\{/)
  assert.match(content, /\.settings-grid\s*\{[\s\S]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/)
  assert.doesNotMatch(content, /class="utility-trigger primary settings-trigger"/)
  assert.doesNotMatch(content, /<div class="assistant-utility-row">[\s\S]*设置[\s\S]*<\/div>/)
  assert.doesNotMatch(content, /<div class="fold-panel">[\s\S]*快捷提问[\s\S]*<\/div>\s*<div v-if="showQuickPanel"/)
})

test('map AI analysis groups operations by workflow stage', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')
  const analysisSection = content.match(/<section v-if="showAnalysisPanel" class="analysis-workbench"[\s\S]*?<\/section>/)?.[0] || ''

  assert.match(analysisSection, /<div class="analysis-flow">/)
  assert.match(analysisSection, /<div class="analysis-flow-group primary">[\s\S]*<span class="flow-group-label">先分析<\/span>[\s\S]*\{\{ primaryAnalyzeLabel \}\}/)
  assert.match(analysisSection, /<div class="analysis-flow-group produce">[\s\S]*<span class="flow-group-label">生成成果<\/span>[\s\S]*v-for="action in resultActions"[\s\S]*@click="runResultAction\(action\)"/)
  assert.match(analysisSection, /<div class="analysis-flow-group utility">[\s\S]*<span class="flow-group-label">辅助<\/span>[\s\S]*@click="previewCurrentPlan">查看执行计划<\/el-button>[\s\S]*@click="copyCurrentContext">复制上下文<\/el-button>/)
  assert.match(content, /const resultActions = computed/)
  assert.match(content, /function runPrimaryAnalysis\(\)/)
  assert.match(content, /function runResultAction\(action: AnalysisResultAction\)/)
  assert.match(content, /type AnalysisResultAction/)
  assert.match(analysisSection, /v-if="contextMode === 'OBJECT'" size="small" plain @click="emit\('close-detail'\)">取消对象<\/el-button>/)
  assert.match(analysisSection, /v-if="contextMode === 'REGION'" size="small" plain @click="emit\('clear-region'\)">清除区域<\/el-button>/)
  assert.doesNotMatch(analysisSection, /class="analysis-actions"/)
  assert.doesNotMatch(analysisSection, /<el-dropdown|更多操作|el-dropdown-item/)
  assert.doesNotMatch(content, /function handleContextCommand/)
})

test('map AI quick suggestions remain available after a conversation starts', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /class="header-icon-btn quick-icon-button"[\s\S]*aria-label="快捷提问"[\s\S]*@click="toggleQuickPanel"/)
  assert.match(content, /<el-icon><ChatDotRound \/><\/el-icon>/)
  assert.match(content, /<section v-if="showQuickPanel" class="quick-panel header-quick-panel">[\s\S]*<div class="quick-list compact-quick-list">/)
  assert.match(content, /function quickAsk\(text: string\)[\s\S]*showQuickPanel\.value = false[\s\S]*send\(\)/)
  assert.match(content, /function toggleQuickPanel\(\)[\s\S]*showAnalysisPanel\.value = false[\s\S]*showToolsPanel\.value = false[\s\S]*showDiagnosticsPanel\.value = false/)
  assert.doesNotMatch(content, /showQuickEntry/)
  assert.doesNotMatch(content, /messages\.value\.length === 0/)
  assert.doesNotMatch(content, /class="assistant-utility-row quick-utility-row"/)
  assert.doesNotMatch(content, /报告草稿/)
})

test('map AI analysis details render directly without a drawer', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /<div class="analysis-summary">\{\{ analysisScopeDescription \}\}<\/div>/)
  assert.match(content, /<div v-if="analysisMetricItems\.length" class="analysis-metrics">/)
  assert.match(content, /<div class="analysis-action-hint">\{\{ operationHint \}\}<\/div>/)
  assert.doesNotMatch(content, /<details class="analysis-detail-drawer">/)
  assert.doesNotMatch(content, /<summary>详情<\/summary>/)
})

test('map AI analysis card avoids duplicated chips and keeps direct summary metrics', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /<span class="analysis-compact-scope">\{\{ analysisCompactScope \}\}<\/span>/)
  assert.match(content, /<section v-if="showAnalysisPanel" class="analysis-workbench"[\s\S]*<span class="analysis-compact-scope">\{\{ analysisCompactScope \}\}<\/span>[\s\S]*<div class="analysis-summary">\{\{ analysisScopeDescription \}\}<\/div>[\s\S]*<div v-if="analysisMetricItems\.length" class="analysis-metrics">/)
  assert.match(content, /function pushMeaningfulCountMetric/)
  assert.match(content, /Number\(value\) <= 1[\s\S]*return/)
  assert.doesNotMatch(content, /key: 'objectCount'/)
  assert.doesNotMatch(content, /class="analysis-context-line detail-context-line"/)
  assert.doesNotMatch(content, /<div class="analysis-context-line">/)
})

test('selected route object is enriched with route-related statistics', () => {
  const oneMapContent = read('src/views/gis/OneMap.vue')
  const contextContent = read('src/utils/gisUnifiedContext.ts')

  assert.match(oneMapContent, /const selectedMapObjectBase = computed/)
  assert.match(oneMapContent, /const selectedObjectStatistics = ref<Record<string, any> \| null>\(null\)/)
  assert.match(oneMapContent, /function selectedRouteRelatedCounts\(/)
  assert.match(oneMapContent, /relatedSectionCount: firstValue\([\s\S]*sectionCount/)
  assert.match(oneMapContent, /relatedDiseaseCount: firstValue\([\s\S]*diseaseCount/)
  assert.match(oneMapContent, /relatedEvaluationUnitCount: firstValue\([\s\S]*unitCount/)
  assert.match(oneMapContent, /relatedAssessmentCount: firstValue\([\s\S]*assessmentCount/)
  assert.match(oneMapContent, /getMapStatistics\(\{[\s\S]*routeCode: routeCode/)
  assert.match(oneMapContent, /watch\([\s\S]*selectedMapObjectBase\.value[\s\S]*loadSelectedObjectStatistics/)
  assert.match(contextContent, /type === 'ROAD_ROUTE'[\s\S]*relatedSectionCount/)
  assert.match(contextContent, /路线相关路段/)
  assert.match(contextContent, /路线相关病害/)
  assert.match(contextContent, /路线相关评定结果/)
})

test('assistant answer keeps one collapsed audit entry below a unified answer card', () => {
  const content = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(content, /<article v-if="item\.role === 'assistant'" class="assistant-response-card">/)
  assert.match(content, /<div class="content assistant-answer-content" v-html="renderMarkdown\(item\.content\)" \/>/)
  assert.match(content, /<details v-if="hasAssistantDetails\(item\)" class="assistant-audit-drawer">/)
  assert.match(content, /<summary class="assistant-audit-summary">[\s\S]*<span>依据 \/ 调用 \/ 执行<\/span>[\s\S]*<em>\{\{ assistantDetailsSummary\(item\) \}\}<\/em>[\s\S]*<\/summary>/)
  assert.match(content, /<details[\s\S]*class="assistant-audit-body"[\s\S]*<AiEvidencePanel[\s\S]*embedded[\s\S]*:default-expanded="true"[\s\S]*<AiTraceButton[\s\S]*label="查看执行过程"[\s\S]*<\/details>/)
  assert.doesNotMatch(content, /assistant-detail-drawer|assistant-detail-summary/)
})

test('assistant audit details avoid nested evidence headers', () => {
  const content = read('src/views/gis/components/map-ai/MapAiConversation.vue')
  const evidenceContent = read('src/views/gis/components/AiEvidencePanel.vue')

  assert.match(content, /<span>依据 \/ 调用 \/ 执行<\/span>/)
  assert.match(evidenceContent, /embedded\?: boolean/)
  assert.match(evidenceContent, /defaultExpanded\?: boolean/)
  assert.match(evidenceContent, /<div v-if="!embedded" class="evidence-header" @click="expanded = !expanded">/)
  assert.match(evidenceContent, /<div v-if="expanded \|\| embedded" class="evidence-body">/)
  assert.match(content, /formatAssistantStatus\(item\.meta\.llmStatus\)/)
  assert.doesNotMatch(content, /依据与调用详情/)
  assert.doesNotMatch(content, /class="message-meta"/)
  assert.doesNotMatch(content, /LLM \$\{item\.meta\.llmStatus\}/)
})

test('assistant evidence panel labels knowledge fallback as retrieval status', () => {
  const evidenceContent = read('src/views/gis/components/AiEvidencePanel.vue')

  assert.match(evidenceContent, /knowledgeFallbackNotice/)
  assert.match(evidenceContent, /知识库状态/)
  assert.match(evidenceContent, /暂无可用向量切片/)
  assert.match(evidenceContent, /不影响业务数据分析/)
  assert.doesNotMatch(evidenceContent, /降级原因/)
  assert.doesNotMatch(evidenceContent, /knowledgeTool\?\.fallback \? 'warning' : 'success'/)
})

test('assistant collapsed detail summary avoids technical runtime labels', () => {
  const content = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(content, /if \(sourceTotal\) parts\.push\(`依据 \$\{sourceTotal\}`\)/)
  assert.match(content, /if \(item\.meta\?\.llmStatus && String\(item\.meta\.llmStatus\)\.toUpperCase\(\) !== 'SUCCESS'\) parts\.push\(`模型 \$\{formatAssistantStatus\(item\.meta\.llmStatus\)\}`\)/)
  assert.doesNotMatch(content, /parts\.push\(`LLM /)
  assert.doesNotMatch(content, /Runtime UP|Tool OK|LLM 关闭/)
})

test('map AI waiting state uses user-facing execution labels', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /查看执行过程/)
  assert.match(content, /'等待执行状态'/)
  assert.doesNotMatch(content, /LangGraph 正在处理|LangGraph 仍在生成|等待 Runtime 上报|查看 Trace/)
})

test('status-only action result cards are hidden from the main conversation', () => {
  const content = read('src/views/gis/components/map-ai/MapAiActionResultPanel.vue')

  assert.match(content, /const visibleResult = computed/)
  assert.match(content, /!props\.result\.markdown && !props\.result\.errorMessage/)
  assert.match(content, /return null[\s\S]*return props\.result/)
  assert.match(content, /<section v-if="visibleResult" class="map-ai-action-result">/)
  assert.doesNotMatch(content, /<section v-if="result" class="map-ai-action-result">/)
  assert.doesNotMatch(content, /props\.result\?\.type === 'ANSWER'/)
})

test('AgentChatFloat honors explicit route scope before viewport fallback', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /preferredContextScope\.value === 'ROUTE'[\s\S]*return 'ROUTE'/)
})

test('solution preview normalizes nested answer meta from tool and response payloads', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /function pickSolutionAnswerMeta/)
  assert.match(content, /actionResult\.answerMeta/)
  assert.match(content, /actionResult\.data\?\.answerMeta/)
  assert.match(content, /res\.data\?\.answerMeta/)
  assert.match(content, /toolResults\?\.find\([\s\S]*data\?\.answerMeta/)
  assert.match(content, /answerMeta: pickSolutionAnswerMeta\(res, actionResult\)/)
})

test('solution preview treats llmStatus success as model-generated advice', () => {
  const content = read('src/views/gis/components/SolutionPreviewDialog.vue')

  assert.match(content, /const llmStatus = String\(meta\.llmStatus \|\| meta\.llm_status \|\| ''\)\.toUpperCase\(\)/)
  assert.match(content, /llmStatus === 'SUCCESS'/)
  assert.match(content, /const answerSource = String\(meta\.answerSource \|\| meta\.answer_source \|\| ''\)\.toUpperCase\(\)/)
  assert.match(content, /本次养护建议已调用大模型生成/)
  assert.match(content, /fallback_reason/)
  assert.doesNotMatch(content, /本次区域养护建议已调用大模型生成/)
})

test('object solution generation does not open a draft preview when action fails', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /function isFailedSolutionResponse/)
  assert.match(content, /normalizeSolutionResponse\(res\)[\s\S]*if \(isFailedSolutionResponse\(normalized\)\)/)
  assert.match(content, /ElMessage\.error\(solutionFailureMessage\(normalized\)\)/)
  assert.match(content, /return[\s\S]*solutionDialogVisible\.value = true/)
})
