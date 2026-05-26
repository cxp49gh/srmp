import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

test('dev compose passes Outline sync controls into backend container', () => {
  const content = read('docker-compose.dev.yml')

  assert.match(content, /OUTLINE_ENABLED: \$\{OUTLINE_ENABLED:-false\}/)
  assert.match(content, /OUTLINE_BASE_URL: \$\{OUTLINE_BASE_URL:-\}/)
  assert.match(content, /OUTLINE_API_TOKEN: \$\{OUTLINE_API_TOKEN:-\}/)
  assert.match(content, /OUTLINE_DEFAULT_COLLECTION_ID: \$\{OUTLINE_DEFAULT_COLLECTION_ID:-\}/)
  assert.match(content, /OUTLINE_SYNC_ENABLED: \$\{OUTLINE_SYNC_ENABLED:-false\}/)
})
