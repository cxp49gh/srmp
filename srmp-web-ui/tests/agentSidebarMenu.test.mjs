import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(new URL('../src/views/agent/components/AgentSidebar.vue', import.meta.url), 'utf8')

test('agent sidebar organizes backend pages by workflow ownership', () => {
  assert.match(content, /const menuSections/)
  assert.match(content, /<div class="subtitle">养护业务与 AI 治理<\/div>/)

  const order = ['工作台', '数据资产', '知识库', 'AI 治理', '系统监控'].map((label) => content.indexOf(`title: '${label}'`))
  order.forEach((index, pos) => assert.notEqual(index, -1, `missing section ${pos}`))
  assert.deepEqual([...order].sort((a, b) => a - b), order)

  assert.match(content, /title: '工作台'[\s\S]*label: 'GIS 一张图', to: '\/gis\/one-map'[\s\S]*label: 'AI 问答', to: '\/agent\/chat'[\s\S]*label: '方案生成', to: '\/agent\/solution-generate'[\s\S]*label: '方案任务', to: '\/agent\/solution-tasks'/)
  assert.match(content, /title: '系统监控'[\s\S]*label: 'AI 运维总览', to: '\/agent\/ai-ops'[\s\S]*label: 'AI 调用监控', to: '\/agent\/ai-traces'[\s\S]*label: 'LangGraph 编排', to: '\/agent\/langgraph-ops'[\s\S]*label: 'AI 健康检查', to: '\/agent\/ai-health'/)
  assert.doesNotMatch(content, /<router-link to="\/agent\/ai-ops">AI 运维总览<\/router-link>\s*<router-link to="\/agent\/langgraph-ops">/)
})

test('agent sidebar uses menu config for collapsible groups and active expansion', () => {
  assert.match(content, /class="nav-item"/)
  assert.match(content, /class="sub-nav-item"/)
  assert.match(content, /const expandedGroups = reactive<Record<string, boolean>>/)
  assert.match(content, /function isGroupActive\(group: NavGroup\)/)
  assert.match(content, /function expandActiveGroups\(path = route\.path\)/)
  assert.match(content, /menuSections\.forEach\(\(section\) => \{[\s\S]*expandActiveEntry\(entry, path\)/)
  assert.doesNotMatch(content, /const outlineNavExpanded = ref\(false\)|const dataMgmtNavExpanded = ref\(false\)/)
  assert.doesNotMatch(content, /function toggleOutlineNav|function toggleDataMgmtNav/)
})
