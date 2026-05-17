import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(new URL('../src/views/gis/components/MapToolbar.vue', import.meta.url), 'utf8')

test('map toolbar draw tools expose readable labels in the query bar', () => {
  assert.match(content, /<div class="draw-section" aria-label="区域框选工具">/)
  assert.match(content, /<span class="draw-section-label">框选<\/span>/)
  assert.match(content, /class="draw-tool-button"[\s\S]*aria-label="矩形框选"/)
  assert.match(content, /aria-label="矩形框选"/)
  assert.match(content, /title="矩形框选"/)
  assert.match(content, /<svg class="icon-svg" aria-hidden="true" focusable="false" viewBox="0 0 24 24">[\s\S]*<rect/)
  assert.match(content, /class="draw-tool-button"[\s\S]*aria-label="多边形框选"/)
  assert.match(content, /aria-label="多边形框选"/)
  assert.match(content, /title="多边形框选"/)
  assert.match(content, /<svg class="icon-svg" aria-hidden="true" focusable="false" viewBox="0 0 24 24">[\s\S]*<path/)
})

test('map toolbar draw tools publish active state and clear affordance', () => {
  assert.match(content, /:aria-pressed="regionMode === 'RECTANGLE'"/)
  assert.match(content, /:aria-pressed="regionMode === 'POLYGON'"/)
  assert.match(content, /class="draw-tool-button draw-clear-button"/)
  assert.match(content, /aria-label="清除框选"/)
  assert.match(content, /title="清除框选"/)
})

test('map toolbar keeps draw tools inside the visible wrapped query bar', () => {
  assert.match(content, /\.query-primary-row\s*\{[\s\S]*flex-wrap:\s*nowrap;[\s\S]*overflow:\s*visible;/)
  assert.match(content, /\.fields-grid\s*\{[\s\S]*flex:\s*1\s+1\s+0;[\s\S]*grid-template-columns:\s*minmax\(210px,\s*1\.25fr\)\s+repeat\(3,\s*minmax\(160px,\s*1fr\)\)\s+minmax\(140px,\s*0\.8fr\);[\s\S]*overflow-x:\s*auto;/)
  assert.match(content, /@media\s*\(max-width:\s*1320px\)\s*\{[\s\S]*\.fields-grid\s*\{[\s\S]*min-width:\s*0;/)
  assert.match(content, /\.draw-section\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-shrink:\s*0;/)
  assert.doesNotMatch(content, /query-extended-row|expand-toggle|el-collapse-transition/)
})

test('map toolbar shows all condition controls in one primary row', () => {
  assert.match(content, /<div class="query-primary-row">[\s\S]*<div class="fields-grid">[\s\S]*label="项目"[\s\S]*label="路网编码"[\s\S]*label="路段专题"[\s\S]*label="指标"[\s\S]*label="等级"[\s\S]*<\/div>\s*<div class="action-group">[\s\S]*查询[\s\S]*重置[\s\S]*<\/div>[\s\S]*<div class="draw-section" aria-label="区域框选工具">/)
  assert.doesNotMatch(content, /{{ isExpanded \? '收起' : '展开' }}/)
})
