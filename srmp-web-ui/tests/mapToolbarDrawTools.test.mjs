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

test('map toolbar keeps draw tools visible without query scrollbars', () => {
  assert.match(content, /\.query-primary-row\s*\{[\s\S]*display:\s*grid;[\s\S]*overflow:\s*visible;/)
  assert.match(content, /\.fields-grid\s*\{[\s\S]*width:\s*100%;[\s\S]*overflow:\s*visible;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)\s*\{[\s\S]*\.query-primary-row\s*\{[\s\S]*display:\s*grid;[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+auto\s+1px\s+auto;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)\s*\{[\s\S]*\.fields-grid\s*\{[\s\S]*grid-template-columns:\s*minmax\(132px,\s*0\.9fr\)\s+minmax\(92px,\s*0\.62fr\)\s+minmax\(94px,\s*0\.62fr\)\s+minmax\(104px,\s*0\.68fr\)\s+minmax\(78px,\s*0\.45fr\);/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*:deep\(\.uniform-item \.el-form-item__label\)\s*\{[\s\S]*width:\s*34px !important;/)
  assert.doesNotMatch(content, /grid-column:\s*1\s*\/\s*-1/)
  assert.doesNotMatch(content, /\.action-group\s*\{[\s\S]*grid-column:\s*1;/)
  assert.doesNotMatch(content, /\.draw-section\s*\{[\s\S]*grid-column:\s*3;/)
  assert.doesNotMatch(content, /:deep\(\.uniform-item \.el-form-item__label\)\s*\{[\s\S]*position:\s*absolute !important;[\s\S]*clip:\s*rect\(0 0 0 0\)/)
  assert.match(content, /\.draw-section\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-shrink:\s*0;/)
  assert.doesNotMatch(content, /overflow-x:\s*auto|overflow:\s*auto/)
  assert.doesNotMatch(content, /query-extended-row|expand-toggle|el-collapse-transition/)
})

test('map toolbar shows all condition controls in one primary row', () => {
  assert.match(content, /<div class="query-primary-row">[\s\S]*<div class="fields-grid">[\s\S]*label="项目"[\s\S]*label="路网"[\s\S]*label="评定"[\s\S]*label="指标"[\s\S]*label="等级"[\s\S]*<\/div>\s*<div class="action-group">[\s\S]*查询[\s\S]*重置[\s\S]*<\/div>[\s\S]*<div class="draw-section" aria-label="区域框选工具">/)
  assert.doesNotMatch(content, /label="路网编码"|label="路段专题"/)
  assert.doesNotMatch(content, /{{ isExpanded \? '收起' : '展开' }}/)
})

test('map toolbar reserves stable width for metric filters and command actions', () => {
  assert.match(content, /\.query-primary-row\s*\{[\s\S]*display:\s*grid;[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+auto\s+1px\s+auto;/)
  assert.match(content, /\.fields-grid\s*\{[\s\S]*grid-template-columns:[\s\S]*minmax\(156px,\s*0\.8fr\)[\s\S]*minmax\(96px,\s*0\.5fr\);/)
  assert.match(content, /\.action-group\s*\{[\s\S]*min-width:\s*104px;[\s\S]*white-space:\s*nowrap;/)
  assert.match(content, /\.action-group :deep\(\.el-button\)\s*\{[\s\S]*min-width:\s*48px;/)
  assert.match(content, /\.draw-btn-group :deep\(\.draw-tool-button\)\s*\{[\s\S]*width:\s*28px;[\s\S]*height:\s*28px;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)/)
})

test('map toolbar stays on one compact row under the agent panel', () => {
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*\.query-primary-row\s*\{[\s\S]*gap:\s*3px;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*:deep\(\.uniform-item \.el-input__wrapper\),[\s\S]*:deep\(\.uniform-item \.el-select__wrapper\)\s*\{[\s\S]*min-height:\s*25px;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*\.action-group\s*\{[\s\S]*min-width:\s*83px;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*\.action-group :deep\(\.el-button\)\s*\{[\s\S]*min-width:\s*40px;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*\.draw-section-label\s*\{[\s\S]*display:\s*none;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*\.draw-btn-group :deep\(\.draw-tool-button\)\s*\{[\s\S]*width:\s*22px;[\s\S]*height:\s*24px;/)
})

test('map toolbar short labels use compact label columns', () => {
  assert.match(content, /:deep\(\.uniform-item \.el-form-item__label\)\s*\{[\s\S]*width:\s*52px !important;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*:deep\(\.uniform-item \.el-form-item__label\)\s*\{[\s\S]*width:\s*34px !important;/)
  assert.match(content, /\.fields-grid\s*\{[\s\S]*grid-template-columns:\s*minmax\(190px,\s*1fr\)\s+minmax\(126px,\s*0\.72fr\)\s+minmax\(126px,\s*0\.72fr\)\s+minmax\(156px,\s*0\.8fr\)\s+minmax\(96px,\s*0\.5fr\);[\s\S]*gap:\s*3px;/)
  assert.match(content, /@media\s*\(max-width:\s*1560px\)[\s\S]*\.fields-grid\s*\{[\s\S]*grid-template-columns:\s*minmax\(132px,\s*0\.9fr\)\s+minmax\(92px,\s*0\.62fr\)\s+minmax\(94px,\s*0\.62fr\)\s+minmax\(104px,\s*0\.68fr\)\s+minmax\(78px,\s*0\.45fr\);[\s\S]*gap:\s*2px;/)
})
