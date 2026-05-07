import test from 'node:test'
import assert from 'node:assert/strict'
import { createLatestRequestGuard } from '../src/utils/latestRequestGuard.ts'

test('only the newest token is accepted', () => {
  const guard = createLatestRequestGuard()

  const first = guard.next()
  const second = guard.next()

  assert.equal(guard.isCurrent(first), false)
  assert.equal(guard.isCurrent(second), true)
})

test('invalidate rejects the current in-flight token', () => {
  const guard = createLatestRequestGuard()

  const token = guard.next()
  guard.invalidate()

  assert.equal(guard.isCurrent(token), false)
})
