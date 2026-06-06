import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(new URL('../src/views/agent/SolutionGeneratePage.vue', import.meta.url), 'utf8')

test('solution generate page uses current solution type vocabulary', () => {
  assert.match(content, /label="路线技术状况报告" value="ROUTE_REPORT"/)
  assert.match(content, /label="路段养护计划" value="SECTION_PLAN"/)
  assert.match(content, /label="评定结果养护建议" value="EVALUATION_UNIT_ADVICE"/)
  assert.match(content, /label="低分评定处置建议" value="LOW_SCORE_TREATMENT"/)
  assert.match(content, /label="病害复核意见" value="DISEASE_REVIEW"/)
  assert.match(content, /solutionType: 'ROUTE_REPORT'/)
  assert.doesNotMatch(content, /LOW_SCORE_SECTION_ANALYSIS|DISEASE_TREATMENT_PLAN|ROAD_ASSESSMENT_REPORT/)
})
