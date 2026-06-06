import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(new URL('../src/views/agent/SolutionTemplatesPage.vue', import.meta.url), 'utf8')

test('solution template page exposes one-map object solution types', () => {
  assert.match(content, /label: '路段养护计划'[\s\S]*value: 'SECTION_PLAN'/)
  assert.match(content, /label: '评定结果养护建议'[\s\S]*value: 'EVALUATION_UNIT_ADVICE'/)
  assert.match(content, /label: '低分评定处置建议'[\s\S]*value: 'LOW_SCORE_TREATMENT'/)
  assert.match(content, /label: '病害复核意见'[\s\S]*value: 'DISEASE_REVIEW'/)
  assert.match(content, /label: '路线技术状况报告'[\s\S]*value: 'ROUTE_REPORT'/)
  assert.match(content, /label: '区域养护建议'[\s\S]*value: 'REGION_MAINTENANCE_SUGGESTION'/)
})

test('solution template page provides dedicated presets for section and assessment templates', () => {
  assert.match(content, /@click="fillSectionPlanDemo"[\s\S]*路段计划/)
  assert.match(content, /@click="fillEvaluationAdviceDemo"[\s\S]*评定建议/)
  assert.match(content, /@click="fillLowScoreTreatmentDemo"[\s\S]*低分处置/)
  assert.match(content, /function fillSectionPlanDemo\(\)/)
  assert.match(content, /function fillEvaluationAdviceDemo\(\)/)
  assert.match(content, /function fillLowScoreTreatmentDemo\(\)/)
})

test('solution template preview variables cover map object evidence sections', () => {
  assert.match(content, /function buildPreviewVariables\(originType: string, objectType: string, solutionType: string\)/)
  assert.match(content, /businessEvidenceSummary:/)
  assert.match(content, /assessmentSummary:/)
  assert.match(content, /diseaseSummary:/)
  assert.match(content, /problemAnalysis:/)
  assert.match(content, /riskNotice:/)
  assert.match(content, /solutionType === 'DISEASE_REVIEW'/)
  assert.match(content, /if \(solutionType === 'SECTION_PLAN' \|\| objectType === 'ROAD_SECTION'\)/)
  assert.match(content, /if \(solutionType === 'EVALUATION_UNIT_ADVICE' \|\| solutionType === 'LOW_SCORE_TREATMENT' \|\| objectType === 'ASSESSMENT_RESULT'\)/)
})
