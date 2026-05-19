import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import {
  buildKnowledgeReadiness,
  knowledgeFallbackDisplayText
} from '../src/utils/aiKnowledgeReadiness.ts'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

test('maps no embedded chunks to an actionable knowledge vector status', () => {
  assert.equal(
    knowledgeFallbackDisplayText('no embedded chunks'),
    '暂无可用向量切片，AI 已尝试关键词检索；请在 AI 运维总览执行补向量或同步入库。'
  )
})

test('builds RAG readiness actions when the knowledge base has no embedded chunks', () => {
  const readiness = buildKnowledgeReadiness({
    knowledgeStats: { documentCount: 2, chunkCount: 18, embeddedChunkCount: 0, vectorEnabled: true },
    outlineStats: { pendingEmbeddingChunkCount: 18 },
    embedding: { available: true }
  })

  assert.equal(readiness.status, 'NO_EMBEDDED_CHUNKS')
  assert.equal(readiness.requiresAction, true)
  assert.equal(readiness.title, '暂无可用向量切片')
  assert.match(readiness.detail, /AI 将使用关键词检索/)
  assert.deepEqual(readiness.actions, ['VECTORIZE_OUTLINE', 'SYNC_OUTLINE', 'VERIFY_KNOWLEDGE'])
})

test('AI ops and health pages expose RAG readiness repair paths', () => {
  const opsContent = read('src/views/agent/AiOpsDashboardPage.vue')
  const healthContent = read('src/views/agent/AiHealthPage.vue')

  assert.match(opsContent, /buildKnowledgeReadiness/)
  assert.match(opsContent, /ragReadiness/)
  assert.match(opsContent, /go\('\/agent\/outline\/sync'\)/)
  assert.match(opsContent, /go\('\/agent\/knowledge-vector'\)/)
  assert.doesNotMatch(opsContent, /go\('\/agent\/outline-auto-sync'\)/)
  assert.match(healthContent, /buildKnowledgeReadiness/)
  assert.match(healthContent, /knowledgeReadiness/)
  assert.match(healthContent, /\/agent\/outline\/sync/)
})
