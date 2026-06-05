import { readFileSync } from 'node:fs'
import test from 'node:test'
import assert from 'node:assert/strict'

const content = readFileSync('srmp-web-ui/src/views/agent/components/SolutionQualityPanel.vue', 'utf8')

test('solution quality panel renders quality closure dimensions', () => {
  assert.match(content, /质量快照/)
  assert.match(content, /props\.quality\?\.dimensions/)
  assert.match(content, /dimensionTagType/)
  assert.match(content, /模板命中/)
  assert.match(content, /业务证据/)
  assert.match(content, /地图关联/)
  assert.match(content, /大模型/)
  assert.match(content, /场景匹配/)
})

test('solution quality panel keeps legacy item list for compatibility', () => {
  assert.match(content, /quality\.items/)
  assert.match(content, /tagType\(item\.level\)/)
  assert.match(content, /item\.penalty/)
})
