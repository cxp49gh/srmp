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

test('MapAiWorkbench exposes plan preview and assistant action events', () => {
  const content = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')

  assert.match(content, /@preview-plan="\$emit\('preview-plan', \$event\)"/)
  assert.match(content, /@generate-default-solution="\$emit\('generate-default-solution'\)"/)
  assert.match(content, /@locate-source="\$emit\('locate-source', \$event\)"/)
  assert.match(content, /@ask-with-source="\$emit\('ask-with-source', \$event\)"/)
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
  assert.match(workbenchContent, /<template #message-tail>[\s\S]*<MapAiSuggestedActions\b[\s\S]*<\/template>\s*<\/MapAiConversation>/)
})

test('map AI wait panel is rendered inside the scrollable conversation area', () => {
  const floatContent = read('src/views/gis/components/AgentChatFloat.vue')
  const workbenchContent = read('src/views/gis/components/map-ai/MapAiWorkbench.vue')

  assert.match(workbenchContent, /<MapAiSuggestedActions\b[\s\S]*\/>\s*<slot name="message-tail" \/>/)
  assert.match(floatContent, /<MapAiWorkbench[\s\S]*>\s*<template #message-tail>[\s\S]*<section v-if="aiBusy" class="ai-wait-panel"/)
  assert.doesNotMatch(floatContent, /<\/MapAiWorkbench>\s*<section v-if="aiBusy" class="ai-wait-panel"/)
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

test('map AI diagnostics render as a compact summary by default', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /const diagnosticsExpanded = ref\(false\)/)
  assert.match(content, /class="diagnostics-summary"/)
  assert.match(content, /v-if="quickDiagnostics && diagnosticsExpanded" class="diagnostics-grid"/)
  assert.doesNotMatch(content, /v-else-if="quickDiagnostics" class="diagnostics-grid"/)
})

test('map AI diagnostics stay inside the data source drawer', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /<div v-if="showToolsPanel" class="option-row compact-options utility-panel">[\s\S]*<section v-if="quickDiagnostics \|\| diagnosticsError" class="diagnostics-panel compact-diagnostics">/)
  assert.doesNotMatch(content, /<\/div>\s*<section v-if="quickDiagnostics \|\| diagnosticsError" class="diagnostics-panel">/)
})

test('map AI optional controls share one compact toolbar row', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /<div class="assistant-utility-row">[\s\S]*数据源：\{\{ optionSummary \}\}[\s\S]*快捷提问[\s\S]*<\/div>/)
  assert.doesNotMatch(content, /<div class="fold-panel">[\s\S]*快捷提问[\s\S]*<\/div>\s*<div v-if="showQuickPanel"/)
})

test('map AI analysis explanation is collapsed by default', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /<details class="analysis-hint-drawer">[\s\S]*<summary>范围说明<\/summary>[\s\S]*\{\{ operationHint \}\}[\s\S]*<\/details>/)
  assert.doesNotMatch(content, /<div class="analysis-action-hint">\{\{ operationHint \}\}<\/div>/)
})

test('assistant operational details are collapsed below the answer by default', () => {
  const content = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(content, /<details v-if="item\.role === 'assistant' && hasAssistantDetails\(item\)" class="assistant-detail-drawer">/)
  assert.match(content, /<summary class="assistant-detail-summary">[\s\S]*依据与调用[\s\S]*<\/summary>[\s\S]*<div v-if="item\.meta" class="message-meta">/)
  assert.match(content, /<details[\s\S]*<AiEvidencePanel[\s\S]*<AiTraceButton[\s\S]*<\/details>/)
})

test('assistant details use concise human-facing labels', () => {
  const content = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(content, /<span>依据与调用<\/span>/)
  assert.match(content, /formatAssistantStatus\(item\.meta\.llmStatus\)/)
  assert.doesNotMatch(content, /依据与调用详情/)
  assert.doesNotMatch(content, /LLM \$\{item\.meta\.llmStatus\}/)
})

test('plain answer action result cards are hidden from the main conversation', () => {
  const content = read('src/views/gis/components/map-ai/MapAiActionResultPanel.vue')

  assert.match(content, /const visibleResult = computed/)
  assert.match(content, /props\.result\?\.type === 'ANSWER'/)
  assert.match(content, /!props\.result\.markdown && !props\.result\.errorMessage/)
  assert.match(content, /<section v-if="visibleResult" class="map-ai-action-result">/)
  assert.doesNotMatch(content, /<section v-if="result" class="map-ai-action-result">/)
})

test('AgentChatFloat honors explicit route scope before viewport fallback', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /preferredContextScope\.value === 'ROUTE'[\s\S]*return 'ROUTE'/)
})
