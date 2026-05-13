import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildPlanWarnings,
  deriveSourceHints,
  normalizeMapAiPlanResponse,
  summarizePlanContext
} from '../src/utils/mapAiPlanPreview.ts'

test('normalizes Java wrapped region plan preview', () => {
  const plan = normalizeMapAiPlanResponse({
    code: 0,
    data: {
      action: 'GENERATE_REGION_SOLUTION',
      intent: 'REGION_ANALYSIS',
      contextSummary: { mode: 'REGION', routeCode: 'G210', year: 2026 },
      toolPlan: [
        {
          toolName: 'gis.queryRegionSummary',
          label: '查询区域统计',
          reason: '查询框选区域或路线统计摘要',
          readOnly: true,
          writeRisk: false,
          args: { routeCode: 'G210', year: 2026, geometry: { type: 'Polygon' } }
        },
        {
          toolName: 'knowledge.retrieve',
          reason: '知识库检索处置规则',
          args: { query: '区域养护', topK: 5 }
        }
      ],
      sourceHints: [{ sourceType: 'MAP_REGION', label: '框选区域', reason: '当前请求包含区域范围。' }],
      warnings: []
    }
  })

  assert.equal(plan.status, 'SUCCESS')
  assert.equal(plan.action, 'GENERATE_REGION_SOLUTION')
  assert.equal(plan.intent, 'REGION_ANALYSIS')
  assert.equal(plan.toolPlan.length, 2)
  assert.equal(plan.toolPlan[0].label, '查询区域统计')
  assert.equal(plan.toolPlan[0].argsSummary.geometryType, 'Polygon')
  assert.equal(plan.toolPlan[1].label, '知识库检索')
  assert.equal(plan.sourceHints.some((item) => item.sourceType === 'BUSINESS_DATA'), true)
  assert.equal(plan.sourceHints.some((item) => item.sourceType === 'KNOWLEDGE'), true)
  assert.equal(plan.sourceHints.some((item) => item.sourceType === 'MAP_REGION'), true)
})

test('derives write warning from planned write tool', () => {
  const warnings = buildPlanWarnings(
    {
      action: 'SAVE_SOLUTION_DRAFT',
      mapContext: { mode: 'OBJECT', mapObject: { objectType: 'DISEASE' } }
    },
    [{ name: 'solution.saveDraft', writeRisk: true }]
  )

  assert.equal(warnings.some((item) => item.code === 'WRITE_TOOL_PLANNED'), true)
})

test('warns when region geometry is missing', () => {
  const plan = normalizeMapAiPlanResponse({
    action: 'ANALYZE_REGION',
    intent: 'REGION_ANALYSIS',
    normalizedRequest: {
      action: 'ANALYZE_REGION',
      mapContext: { mode: 'REGION', routeCode: 'G210', year: 2026, regionSummary: { routeCount: 1 } }
    },
    toolPlan: [{ toolName: 'gis.queryRegionSummary', reason: '查询区域统计', args: { routeCode: 'G210' } }]
  })

  assert.equal(plan.warnings.some((item) => item.code === 'REGION_GEOMETRY_MISSING'), true)
})

test('summarizes object and region context', () => {
  assert.deepEqual(
    summarizePlanContext({
      contextSummary: { routeCode: 'G210', year: 2026 },
      normalizedRequest: { mapContext: { mode: 'OBJECT', mapObject: { objectType: 'DISEASE' } } }
    }).slice(0, 3),
    ['对象模式', '路线 G210', '年度 2026']
  )

  assert.equal(
    deriveSourceHints({
      normalizedRequest: { mapContext: { mode: 'REGION', geometry: { type: 'Polygon' } } },
      toolPlan: [{ name: 'template.match' }]
    }).some((item) => item.sourceType === 'TEMPLATE'),
    true
  )
})
