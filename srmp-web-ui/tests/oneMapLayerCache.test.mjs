import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(new URL('../src/views/gis/OneMap.vue', import.meta.url), 'utf8')

test('asset and business layer toggles reuse loaded layers without querying again', () => {
  assert.match(content, /const layerSignatures = new Map<string, string>\(\)/)
  assert.match(content, /function hideLayerByKey\(layerKey: string\)/)
  assert.match(content, /function showCachedLayer\(layerKey: string\)/)
  assert.match(content, /function layerCacheMatches\(layerKey: string\)/)
  assert.match(content, /else if \(!showCachedLayer\('roadRoute'\)\) tasks\.push\(loadLayerSafely\('roadRoute', \(\) => getRoadRoutes\(params\)\)\)/)
  assert.match(content, /else if \(!showCachedLayer\('roadSection'\)\) tasks\.push\(loadLayerSafely\('roadSection', \(\) => getRoadSections\(params\)\)\)/)
  assert.match(content, /else if \(!showCachedLayer\('assessment'\)\) tasks\.push\(loadLayerSafely\('assessment', \(\) => getAssessmentResults\(params\)\)\)/)
  assert.match(content, /if \(!layers\.roadRoute\) hideLayerByKey\('roadRoute'\)/)
  assert.match(content, /if \(!layers\.roadSection\) hideLayerByKey\('roadSection'\)/)
  assert.match(content, /if \(!layers\.assessment\) hideLayerByKey\('assessment'\)/)
})

test('explicit search and refresh still discard cached layer data', () => {
  assert.match(content, /function discardLayerByKey\(layerKey: string\)/)
  assert.match(content, /layerSignatures\.clear\(\)/)
  assert.match(content, /discardLayerByKey\(layerKey\)/)
})
