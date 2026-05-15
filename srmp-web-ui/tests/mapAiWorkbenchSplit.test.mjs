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

test('map AI conversation scrolls new action results into view', () => {
  const conversationContent = read('src/views/gis/components/map-ai/MapAiConversation.vue')

  assert.match(conversationContent, /<div ref="messageListRef" class="conversation-message-list">/)
  assert.match(conversationContent, /watch\(\s*\(\)\s*=>\s*props\.messages\.length[\s\S]*scrollMessageListToBottom/)
  assert.match(conversationContent, /messageListRef\.value\.scrollTop\s*=\s*messageListRef\.value\.scrollHeight/)
})

test('map AI panel compacts nonessential analysis text on short viewports', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /@media\s*\(max-height:\s*700px\)\s*\{[\s\S]*\.analysis-summary,[\s\S]*\.analysis-action-hint\s*\{[\s\S]*display:\s*none;/)
  assert.match(content, /@media\s*\(max-height:\s*700px\)\s*\{[\s\S]*\.analysis-metrics\s*\{[\s\S]*display:\s*none;/)
})

test('AgentChatFloat honors explicit route scope before viewport fallback', () => {
  const content = read('src/views/gis/components/AgentChatFloat.vue')

  assert.match(content, /preferredContextScope\.value === 'ROUTE'[\s\S]*return 'ROUTE'/)
})
