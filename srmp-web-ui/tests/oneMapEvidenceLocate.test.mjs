import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(
  new URL('../src/views/gis/OneMap.vue', import.meta.url),
  'utf8'
)

test('verified object lookup prefers exact layer match before geometry fallback', () => {
  const locateStart = content.indexOf(
    'function locateMapTarget(target: GisSourceMapTarget)'
  )
  const exactMatch = content.indexOf(
    'const matched = target.objectId ? findLayerByTarget(target) : null',
    locateStart
  )
  const geometryFallback = content.indexOf(
    'if (target.geometry)',
    locateStart
  )
  assert.ok(exactMatch > locateStart)
  assert.ok(geometryFallback > exactMatch)
})

test('feature matching includes top-level GeoJSON feature id', () => {
  assert.match(
    content,
    /const featureId = firstValue\([\s\S]*?feature\?\.id[\s\S]*?\)/
  )
})

test('route summaries use a temporary source highlight overlay', () => {
  assert.match(content, /function isRouteSummaryTarget\(/)
  assert.match(content, /function highlightRouteSummaryTarget\(/)
  assert.match(content, /aiSourceHighlightLayer = L\.geoJSON/)
  assert.match(
    content,
    /async function handleFeatureClick[\s\S]*?clearAiSourceHighlight\(\)/
  )
  assert.match(
    content,
    /function clearSelection\(\)[\s\S]*?clearAiSourceHighlight\(\)/
  )
})

test('disease source loading refuses a missing verified bbox', () => {
  assert.match(content, /function hasValidSourceBbox\(/)
  assert.match(
    content,
    /病害来源已验证，但缺少可用空间位置/
  )
})
