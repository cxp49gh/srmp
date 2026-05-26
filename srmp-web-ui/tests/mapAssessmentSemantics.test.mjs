import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
const semanticsPath = resolve(root, 'src/utils/mapAssessmentSemantics.ts')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

test('assessment result actions use metric-aware semantics instead of object-type low-score wording', async () => {
  assert.equal(existsSync(semanticsPath), true)
  const semantics = await import(pathToFileURL(semanticsPath).href)

  const goodAssessment = {
    objectType: 'ASSESSMENT_RESULT',
    routeCode: 'Y016140727',
    mqi: 85.067,
    pqi: 75.111,
    pci: 77.614,
    activeMetricGrade: 'GOOD',
    activeMetricValue: 85.067
  }

  assert.equal(semantics.isLowAssessmentResult(goodAssessment, 'MQI'), false)
  assert.equal(semantics.assessmentAnalyzeLabel(goodAssessment, 'MQI'), '分析评定结果')
  assert.deepEqual(semantics.assessmentSolutionAction(goodAssessment, 'MQI'), {
    type: 'EVALUATION_UNIT_ADVICE',
    label: '生成评定养护建议'
  })
  assert.match(semantics.assessmentOperationHint(goodAssessment, 'MQI'), /评定结果/)
  assert.doesNotMatch(semantics.assessmentOperationHint(goodAssessment, 'MQI'), /低分/)
})

test('poor or bad assessment result keeps low-score analysis and treatment wording', async () => {
  assert.equal(existsSync(semanticsPath), true)
  const semantics = await import(pathToFileURL(semanticsPath).href)

  const poorAssessment = {
    objectType: 'ASSESSMENT_RESULT',
    routeCode: 'Y016140727',
    mqi: 65.2,
    activeMetricGrade: 'POOR',
    activeMetricValue: 65.2
  }

  assert.equal(semantics.isLowAssessmentResult(poorAssessment, 'MQI'), true)
  assert.equal(semantics.assessmentAnalyzeLabel(poorAssessment, 'MQI'), '分析低分原因')
  assert.deepEqual(semantics.assessmentSolutionAction(poorAssessment, 'MQI'), {
    type: 'LOW_SCORE_TREATMENT',
    label: '生成低分处置建议'
  })
  assert.match(semantics.assessmentOperationHint(poorAssessment, 'MQI'), /低分/)
})

test('map AI assessment components delegate labels to shared metric-aware semantics', () => {
  const floatContent = read('src/views/gis/components/AgentChatFloat.vue')
  const suggestedContent = read('src/views/gis/components/map-ai/MapAiSuggestedActions.vue')

  assert.match(floatContent, /assessmentAnalyzeLabel/)
  assert.match(floatContent, /assessmentSolutionAction/)
  assert.match(floatContent, /assessmentOperationHint/)
  assert.doesNotMatch(floatContent, /return '分析低分原因'/)
  assert.doesNotMatch(floatContent, /label:\s*'生成低分处置建议',\s*primary:\s*true/)

  assert.match(suggestedContent, /assessmentSolutionLabel/)
  assert.doesNotMatch(suggestedContent, /return '生成低分处置建议'/)
})
