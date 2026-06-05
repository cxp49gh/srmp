import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import ts from 'typescript'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)
const sourcePath = resolve(root, 'src/utils/mapAiContext.ts')
const source = readFileSync(sourcePath, 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020
  }
}).outputText

const module = { exports: {} }
vm.runInNewContext(compiled, {
  exports: module.exports,
  module,
  require,
  console
}, { filename: sourcePath })

const { buildMapAiContextPayload } = module.exports

const routeContext = buildMapAiContextPayload({
  mode: 'ROUTE',
  message: '分析路线',
  context: {
    query: {
      projectId: 'project-1',
      routeCode: 'G210',
      indexCode: 'MQI'
    }
  }
})

assert.equal(routeContext.year, undefined)
assert.equal(JSON.stringify(routeContext).includes('2026'), false)

const objectContext = buildMapAiContextPayload({
  mode: 'OBJECT',
  message: '分析对象',
  context: {
    query: {
      projectId: 'project-1',
      indexCode: 'MQI'
    }
  },
  mapObject: {
    objectType: 'ROAD_SECTION',
    routeCode: 'G210',
    objectId: 'section-1',
    year: 2026
  }
})

assert.equal(objectContext.year, undefined)
assert.equal(objectContext.mapObject.year, 2026)
