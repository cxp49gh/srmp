import test from 'node:test'
import assert from 'node:assert/strict'
import { hasLocatableTarget, sourceToMapTarget } from '../src/utils/gisUnifiedContext.ts'

test('sourceToMapTarget prefers standard mapTarget contract', () => {
  const source = {
    sourceType: 'BUSINESS_DATA',
    sourceTitle: '病害｜裂缝｜Y016140727｜K1.2',
    mapTarget: {
      objectType: 'DISEASE',
      objectId: 'disease-1',
      routeCode: 'Y016140727',
      startStake: 1.2,
      endStake: 1.25,
      title: '病害｜裂缝'
    },
    metadata: {
      routeCode: 'WRONG'
    }
  }

  const target = sourceToMapTarget(source)

  assert.equal(target.objectType, 'DISEASE')
  assert.equal(target.objectId, 'disease-1')
  assert.equal(target.routeCode, 'Y016140727')
  assert.equal(target.startStake, 1.2)
  assert.equal(target.endStake, 1.25)
  assert.equal(target.title, '病害｜裂缝')
  assert.equal(hasLocatableTarget(target), true)
})

test('sourceToMapTarget falls back to followupContext mapTarget', () => {
  const source = {
    sourceType: 'KNOWLEDGE',
    sourceTitle: '路面技术状况评定标准',
    followupContext: {
      mapTarget: {
        objectType: 'ASSESSMENT_RESULT',
        objectId: 'assessment-1',
        routeCode: 'Y016140727',
        startStake: 0,
        endStake: 0.1
      }
    }
  }

  const target = sourceToMapTarget(source)

  assert.equal(target.objectType, 'ASSESSMENT_RESULT')
  assert.equal(target.objectId, 'assessment-1')
  assert.equal(target.routeCode, 'Y016140727')
  assert.equal(hasLocatableTarget(target), true)
})
