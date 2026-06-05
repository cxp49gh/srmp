import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const page = readFileSync(new URL('../src/views/agent/SolutionEvalPage.vue', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/solution.ts', import.meta.url), 'utf8')
const router = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const sidebar = readFileSync(new URL('../src/views/agent/components/AgentSidebar.vue', import.meta.url), 'utf8')
const ops = readFileSync(new URL('../src/views/agent/AiOpsDashboardPage.vue', import.meta.url), 'utf8')

test('solution eval page exposes one-click regression checks for solution quality closure', () => {
  assert.match(page, /title="方案回归评测"/)
  assert.match(page, /加载默认用例/)
  assert.match(page, /执行评测/)
  assert.match(page, /最近任务 \/ 内置样例/)
  assert.match(page, /质量快照/)
  assert.match(page, /模板命中/)
  assert.match(page, /业务证据/)
  assert.match(page, /地图关联/)
  assert.match(page, /场景匹配/)
  assert.match(page, /ROUTE_REPORT/)
  assert.match(page, /SECTION_PLAN/)
  assert.match(page, /EVALUATION_UNIT_ADVICE/)
  assert.match(page, /LOW_SCORE_TREATMENT/)
  assert.match(page, /DISEASE_TREATMENT/)
  assert.match(page, /REGION_MAINTENANCE_SUGGESTION/)
})

test('solution eval api, route and navigation are wired for administrators', () => {
  assert.match(api, /getDefaultSolutionEvalCases/)
  assert.match(api, /runSolutionEval/)
  assert.match(api, /\/api\/ai\/solution\/eval\/cases/)
  assert.match(api, /\/api\/ai\/solution\/eval\/run/)
  assert.match(router, /import SolutionEvalPage/)
  assert.match(router, /path: 'solution-eval'[\s\S]*component: SolutionEvalPage[\s\S]*title: '方案回归评测'/)
  assert.match(sidebar, /label: '方案回归评测', to: '\/agent\/solution-eval'/)
  assert.match(sidebar, /activePrefixes: \['\/agent\/ai-governance', '\/agent\/solution-templates', '\/agent\/solution-eval'\]/)
  assert.match(ops, /go\('\/agent\/solution-eval'\)/)
})
