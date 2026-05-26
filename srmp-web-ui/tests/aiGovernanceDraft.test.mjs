import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import { test } from 'node:test'
import ts from '../node_modules/typescript/lib/typescript.js'

const source = readFileSync('srmp-web-ui/src/views/agent/components/governanceDraft.ts', 'utf8')
const require = createRequire(import.meta.url)

function plain(value) {
  return JSON.parse(JSON.stringify(value))
}

function loadGovernanceDraftModule() {
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  }).outputText
  const module = { exports: {} }
  vm.runInNewContext(output, { module, exports: module.exports, require })
  return module.exports
}

test('governanceDraft exports matrix and evaluation helpers', () => {
  for (const name of [
    'buildCapabilityMatrixRows',
    'updateCapabilityToolPolicy',
    'extractEvaluationCases',
    'writeEvaluationCases',
    'derivePublishBlockers'
  ]) {
    assert.match(source, new RegExp(`export function ${name}\\b`))
  }
})

test('policy editor removes a tool from conflicting buckets', async () => {
  const module = loadGovernanceDraftModule()
  const config = {
    version: 'test',
    capabilities: [
      {
        id: 'knowledge.metric_explain',
        name: '指标解释',
        toolPolicy: {
          required: ['knowledge.retrieve'],
          optional: ['gis.queryRegionSummary'],
          adaptive: ['knowledge.retrieve'],
          prohibited: []
        }
      }
    ]
  }

  const next = module.updateCapabilityToolPolicy(config, 'knowledge.metric_explain', 'prohibited', ['knowledge.retrieve'])
  const policy = next.capabilities[0].toolPolicy
  assert.deepEqual(plain(policy.required), [])
  assert.deepEqual(plain(policy.optional), ['gis.queryRegionSummary'])
  assert.deepEqual(plain(policy.adaptive), [])
  assert.deepEqual(plain(policy.prohibited), ['knowledge.retrieve'])
})

test('evaluation cases round-trip through capability examples', async () => {
  const module = loadGovernanceDraftModule()
  const config = {
    version: 'test',
    capabilities: [
      {
        id: 'map.route_analysis',
        name: '路线分析',
        examples: [
          {
            id: 'map.route_analysis.action',
            name: '点击分析路线',
            request: { action: 'ANALYZE_ROUTE', message: '分析当前路线' },
            expect: { capabilityId: 'map.route_analysis', requiredTools: ['gis.queryRegionSummary'] }
          }
        ]
      }
    ]
  }

  const cases = module.extractEvaluationCases(config)
  assert.equal(cases.length, 1)
  assert.equal(cases[0].capabilityId, 'map.route_analysis')
  const updated = [{ ...cases[0], name: '路线分析改名', requiredTools: ['gis.queryDiseases'] }]
  const next = module.writeEvaluationCases(config, updated)
  assert.equal(next.capabilities[0].examples[0].name, '路线分析改名')
  assert.deepEqual(plain(next.capabilities[0].examples[0].expect.requiredTools), ['gis.queryDiseases'])
})
