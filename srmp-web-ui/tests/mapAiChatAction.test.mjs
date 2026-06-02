import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveChatAction } from '../src/utils/mapAiChatAction.ts'

test('metric explanation remains free chat in route context', () => {
  assert.equal(resolveChatAction('ROUTE', '解释 PCI 指标'), 'CHAT')
  assert.equal(resolveChatAction('ROUTE', 'PCI 指标是什么意思？'), 'CHAT')
})

test('explicit route analysis still uses route action', () => {
  assert.equal(resolveChatAction('ROUTE', '分析当前路线整体路况'), 'ANALYZE_ROUTE')
  assert.equal(resolveChatAction('ROUTE', '结合当前查询条件、地图视野和启用图层，找出次差路段'), 'ANALYZE_ROUTE')
})

test('object and region analysis actions remain explicit', () => {
  assert.equal(resolveChatAction('OBJECT', '分析当前地图选中对象，说明主要问题'), 'ANALYZE_OBJECT')
  assert.equal(resolveChatAction('REGION', '综合分析当前区域内线路、路段、评定单元、病害和评定结果'), 'ANALYZE_REGION')
})

test('non-analysis questions do not inherit map context action', () => {
  assert.equal(resolveChatAction('OBJECT', '裂缝处置规范依据是什么'), 'CHAT')
  assert.equal(resolveChatAction('REGION', '怎么计算 MQI？'), 'CHAT')
})
