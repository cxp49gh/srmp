import test from 'node:test'
import assert from 'node:assert/strict'
import {
  geometryBbox,
  sourceLayerKey,
  sourceTargetQuery
} from '../src/utils/gisSourceLayer.ts'

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

test('geometryBbox pads a Point into a valid viewport', () => {
  assert.deepEqual(
    geometryBbox({
      type: 'Point',
      coordinates: [112.123, 37.456]
    }),
    [112.1225, 37.4555, 112.1235, 37.4565]
  )
})

test('geometryBbox spans nested line and polygon coordinates', () => {
  assert.deepEqual(
    geometryBbox({
      type: 'LineString',
      coordinates: [
        [112.1, 37.2],
        [112.4, 37.5]
      ]
    }),
    [112.1, 37.2, 112.4, 37.5]
  )
})

test('geometryBbox rejects invalid coordinates', () => {
  assert.equal(
    geometryBbox({ type: 'Point', coordinates: ['bad', 37.4] }),
    undefined
  )
})

test('sourceTargetQuery uses verified geometry bbox for disease loading', () => {
  assert.deepEqual(
    sourceTargetQuery(
      { projectId: 'project-1', indexCode: 'MQI' },
      {
        objectType: 'DISEASE',
        routeCode: 'C001140727',
        startStake: 0,
        endStake: 0,
        geometry: {
          type: 'Point',
          coordinates: [112.123, 37.456]
        }
      }
    ),
    {
      projectId: 'project-1',
      indexCode: 'MQI',
      routeCode: 'C001140727',
      stakeStart: 0,
      stakeEnd: 0,
      minLng: 112.1225,
      minLat: 37.4555,
      maxLng: 112.1235,
      maxLat: 37.4565
    }
  )
})
