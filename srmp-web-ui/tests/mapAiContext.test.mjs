import test from 'node:test'
import assert from 'node:assert/strict'
import { buildMapAiContextPayload } from '../src/utils/mapAiContext.ts'
import { buildUnifiedAnalysisTargets } from '../src/utils/gisUnifiedContext.ts'

test('object context prefers selected object route over toolbar query route', () => {
  const payload = buildMapAiContextPayload({
    mode: 'OBJECT',
    message: '分析当前对象',
    context: {
      query: { routeCode: 'G210', year: 2026 },
      selectedLayers: ['ROAD_ROUTE', 'DISEASE']
    },
    mapObject: {
      objectType: 'ASSESSMENT_RESULT',
      routeCode: 'Y016140727',
      year: 2026,
      startStake: 0,
      endStake: 14.072
    }
  })

  assert.equal(payload.routeCode, 'Y016140727')
  assert.equal(payload.year, 2026)
  assert.equal(payload.mapObject.routeCode, 'Y016140727')
})

test('region context exposes standard geometry field for backend tools', () => {
  const geometry = {
    type: 'Polygon',
    coordinates: [[[112, 37], [113, 37], [113, 38], [112, 37]]]
  }
  const payload = buildMapAiContextPayload({
    mode: 'REGION',
    message: '分析区域',
    context: {
      query: { routeCode: 'G210', year: 2026 },
      regionGeometry: geometry,
      selectedLayers: ['ROAD_SECTION', 'DISEASE'],
      regionSummary: { routeCount: 1 }
    },
    region: { objectType: 'MAP_REGION', geometry },
    regionSummary: { routeCount: 1 }
  })

  assert.deepEqual(payload.geometry, geometry)
  assert.deepEqual(payload.regionGeometry, geometry)
  assert.equal(payload.routeCode, 'G210')
})

test('road section analysis targets include related business objects', () => {
  const targets = buildUnifiedAnalysisTargets({
    mapObject: {
      objectType: 'ROAD_SECTION',
      routeCode: 'Y016140727',
      startStake: 0,
      endStake: 14.072,
      relatedEvaluationUnitCount: 8,
      relatedDiseaseCount: 13,
      relatedAssessmentCount: 2
    },
    query: { routeCode: 'G210' }
  })

  assert.deepEqual(targets.map((item) => item.label), [
    '路段',
    '路段相关评定单元',
    '路段相关病害',
    '路段相关评定结果'
  ])
  assert.equal(targets[0].routeCode, 'Y016140727')
  assert.equal(targets[1].count, 8)
  assert.equal(targets[2].count, 13)
  assert.equal(targets[3].count, 2)
})
