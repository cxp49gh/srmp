import test from 'node:test'
import assert from 'node:assert/strict'
import { sourceLayerKey, sourceTargetQuery } from '../src/utils/gisSourceLayer.ts'

test('sourceLayerKey maps locatable business objects to their map layers', () => {
  assert.equal(sourceLayerKey({ objectType: 'ROAD_ROUTE' }), 'roadRoute')
  assert.equal(sourceLayerKey({ objectType: 'ROAD_SECTION' }), 'roadSection')
  assert.equal(sourceLayerKey({ objectType: 'DISEASE' }), 'disease')
  assert.equal(sourceLayerKey({ objectType: 'ASSESSMENT_RESULT' }), 'assessment')
  assert.equal(sourceLayerKey({ objectType: 'MAP_REGION', geometry: { type: 'Polygon', coordinates: [] } }), '')
})

test('sourceTargetQuery narrows automatic layer loading to route and stake range', () => {
  assert.deepEqual(
    sourceTargetQuery(
      {
        projectId: 'project-1',
        routeCode: 'G210',
        indexCode: 'MQI',
        grade: 'GOOD',
        diseaseType: 'CRACK',
        severity: 'HEAVY',
        minLng: 112.1,
        minLat: 37.1,
        maxLng: 112.2,
        maxLat: 37.2,
        zoom: 11
      },
      { routeCode: 'Y016140727', startStake: 1.2, endStake: 1.25 }
    ),
    {
      projectId: 'project-1',
      routeCode: 'Y016140727',
      indexCode: 'MQI',
      stakeStart: 1.2,
      stakeEnd: 1.25
    }
  )
})
