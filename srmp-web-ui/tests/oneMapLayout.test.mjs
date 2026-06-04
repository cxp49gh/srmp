import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(new URL('../src/views/gis/OneMap.vue', import.meta.url), 'utf8')

test('one map left stack starts close to the one-row compact top toolbar', () => {
  assert.match(content, /\.left-map-stack\s*\{[\s\S]*top:\s*64px;[\s\S]*gap:\s*8px;[\s\S]*width:\s*292px;/)
  assert.match(content, /@media\s*\(max-width:\s*1280px\)\s*\{[\s\S]*\.left-map-stack\s*\{[\s\S]*top:\s*64px;/)
  assert.doesNotMatch(content, /\.left-map-stack\s*\{[\s\S]*top:\s*88px;/)
  assert.doesNotMatch(content, /@media\s*\(max-width:\s*1280px\)\s*\{[\s\S]*\.left-map-stack\s*\{[\s\S]*top:\s*106px;/)
  assert.doesNotMatch(content, /\.left-map-stack\s*\{[\s\S]*top:\s*126px;/)
})
