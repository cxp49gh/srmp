import test from 'node:test'
import assert from 'node:assert/strict'
import { hasLocatableTarget, sourceToMapTarget } from '../src/utils/gisUnifiedContext.ts'

test('sourceToMapTarget prefers standard mapTarget contract', () => {
  const source = {
    sourceType: 'BUSINESS_DATA',
    sourceTitle: '病害｜裂缝｜Y016140727｜K1.2',
    bindingType: 'OBJECT',
    bindingOrigin: 'BUSINESS_QUERY',
    bindingStatus: 'UNVERIFIED',
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

test('strict binding contract controls locatability', () => {
  const source = {
    sourceType: 'BUSINESS_DATA',
    bindingType: 'OBJECT',
    bindingStatus: 'UNVERIFIED',
    mapTarget: {
      objectType: 'DISEASE',
      objectId: 'disease-1',
      routeCode: 'Y016140727'
    }
  }

  assert.equal(hasLocatableTarget(sourceToMapTarget(source)), true)
})

test('reference-only source ignores incidental route code', () => {
  const source = {
    sourceType: 'KNOWLEDGE',
    bindingType: 'NONE',
    bindingStatus: 'VALID',
    routeCode: 'Y016140727'
  }

  assert.equal(hasLocatableTarget(sourceToMapTarget(source)), false)
})

test('historical source without binding contract is not locatable', () => {
  const source = {
    sourceType: 'BUSINESS_DATA',
    mapTarget: { objectType: 'DISEASE', objectId: 'legacy-1' }
  }

  assert.equal(hasLocatableTarget(sourceToMapTarget(source)), false)
})

test('not found and invalid bindings are not locatable', () => {
  for (const bindingStatus of ['NOT_FOUND', 'INVALID']) {
    const source = {
      bindingType: 'OBJECT',
      bindingStatus,
      mapTarget: { objectType: 'DISEASE', objectId: 'disease-1' }
    }
    assert.equal(hasLocatableTarget(sourceToMapTarget(source)), false)
  }
})
