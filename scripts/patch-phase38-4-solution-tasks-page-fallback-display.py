#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
VUE = ROOT / "srmp-web-ui/src/views/agent/SolutionTasksPage.vue"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

if not VUE.exists():
    fail("文件不存在：" + str(VUE))

bak = VUE.with_suffix(VUE.suffix + ".phase38-4.bak")
if not bak.exists():
    bak.write_text(VUE.read_text(encoding="utf-8"), encoding="utf-8")

s = VUE.read_text(encoding="utf-8")

# 增加前端兜底修复函数：如果后端旧任务 result_content 已经是带空字段的兜底模板，则页面展示时用 detail/mapObject/aiContext 修复。
if "function buildFallbackMarkdownForDisplay" not in s:
    marker = "function exportMarkdown() {"
    helper = r'''
function valueOfAny(obj: any, keys: string[]) {
  if (!obj) return ''
  for (const key of keys) {
    const value = obj[key]
    if (value !== undefined && value !== null && String(value).trim() !== '') return value
  }
  if (obj.raw) {
    for (const key of keys) {
      const value = obj.raw[key]
      if (value !== undefined && value !== null && String(value).trim() !== '') return value
    }
  }
  return ''
}

function formatStakeValue(value: any) {
  if (value === undefined || value === null || String(value).trim() === '') return ''
  return `K${String(value).replace(/\.0+$/, '')}`
}

function buildFallbackMarkdownForDisplay(task: any) {
  const mapObject = task?.map_object || task?.mapObject || task?.object_summary?.mapObject || task?.objectSummary?.mapObject || {}
  const aiRaw = aiContext.value?.aiContext?.raw || aiContext.value?.aiContext || {}
  const aiMapObject = aiRaw?.mapObject || aiRaw?.map_context?.mapObject || aiRaw?.mapContext?.mapObject || {}

  const merged = {
    ...(task || {}),
    ...(task?.object_summary || task?.objectSummary || {}),
    ...(mapObject || {}),
    ...(mapObject?.raw || {}),
    ...(aiRaw || {}),
    ...(aiMapObject || {}),
    ...(aiMapObject?.raw || {})
  }

  const solutionType = valueOfAny(merged, ['solutionType', 'solution_type']) || '-'
  const routeCode = valueOfAny(merged, ['routeCode', 'route_code']) || '-'
  const year = valueOfAny(merged, ['year']) || '-'
  const objectType = valueOfAny(merged, ['objectType', 'object_type', 'type', 'layerType']) || '-'
  const startStake = valueOfAny(merged, ['startStake', 'start_stake', 'startMileage', 'start_mileage'])
  const endStake = valueOfAny(merged, ['endStake', 'end_stake', 'endMileage', 'end_mileage'])
  const diseaseName = valueOfAny(merged, ['diseaseName', 'disease_name', 'diseaseType', 'disease_type'])
  const severity = valueOfAny(merged, ['severity', 'grade', 'level'])
  const quantity = valueOfAny(merged, ['quantity', 'area', 'length'])
  const unit = valueOfAny(merged, ['measureUnit', 'measure_unit', 'unit'])
  const aiAnswer = aiContext.value?.aiAnswer || ''

  let md = '# AI 方案草稿（系统兜底模板）\n\n'
  md += '> 未匹配到可用方案模板，或模板渲染结果为空；系统已使用内置兜底模板生成草稿，需人工复核后使用。\n\n'
  md += '## 一、基础信息\n\n'
  md += '| 字段 | 内容 |\n|---|---|\n'
  md += `| 方案类型 | ${solutionType} |\n`
  md += `| 路线编号 | ${routeCode} |\n`
  md += `| 年度 | ${year} |\n`
  md += `| 对象类型 | ${objectType} |\n`
  if (startStake || endStake) md += `| 桩号范围 | ${formatStakeValue(startStake)}${endStake ? '-' + formatStakeValue(endStake) : ''} |\n`
  if (diseaseName) md += `| 病害类型 | ${diseaseName} |\n`
  if (severity) md += `| 严重程度/等级 | ${severity} |\n`
  if (quantity) md += `| 数量 | ${quantity}${unit || ''} |\n`
  md += '\n## 二、AI 分析摘要\n\n'
  md += aiAnswer || '暂无 AI 分析摘要。建议结合当前地图对象、知识库资料和现场复核结果完善方案。'
  md += '\n\n## 三、主要问题\n\n'
  md += diseaseName
    ? `- 当前对象涉及 ${diseaseName}，需结合严重程度、影响范围、周边病害和评定结果综合判断。\n`
    : `- 当前对象类型为 ${objectType}，需结合地图上下文、评定指标、病害分布和养护规则综合判断。\n`
  md += '- 若存在连续病害、低分单元或重度病害，应优先安排现场复核。\n\n'
  md += '## 四、处置建议\n\n'
  md += '- 点状病害可采用局部修补、裂缝处置、坑槽修补等措施。\n'
  md += '- 连续病害或低分区间可考虑封层、薄层罩面、局部铣刨重铺或中修。\n'
  md += '- 重度或影响安全的对象应优先纳入近期处置计划。\n\n'
  md += '## 五、实施与复核要求\n\n'
  md += '- 复核病害位置、范围、面积/长度、严重程度和发展趋势。\n'
  md += '- 核查排水、基层、路基、重复修补区域和交通安全风险。\n'
  md += '- 根据现场复核结果调整工程量、工艺和处置边界。\n'
  return md
}

function displayResultContent(task: any) {
  const content = task?.result_content || task?.resultContent || ''
  if (!content) return buildFallbackMarkdownForDisplay(task)
  const hasEmptyFallbackField = content.includes('系统兜底模板') && (
    content.includes('| 路线编号 | - |') ||
    content.includes('| 年度 | - |') ||
    content.includes('| 对象类型 | - |') ||
    content.includes('| 方案类型 | - |')
  )
  return hasEmptyFallbackField ? buildFallbackMarkdownForDisplay(task) : content
}

'''
    if marker in s:
        s = s.replace(marker, helper + "\n" + marker, 1)
    else:
        s += "\n" + helper

# 尽量把模板里直接显示 result_content/resultContent 的地方替换为 displayResultContent(detail)
replacements = [
    ("{{ detail.result_content }}", "{{ displayResultContent(detail) }}"),
    ("{{ detail.resultContent }}", "{{ displayResultContent(detail) }}"),
    ("v-html=\"detail.result_content\"", "v-html=\"displayResultContent(detail)\""),
    ("v-html=\"detail.resultContent\"", "v-html=\"displayResultContent(detail)\""),
    (":content=\"detail.result_content\"", ":content=\"displayResultContent(detail)\""),
    (":content=\"detail.resultContent\"", ":content=\"displayResultContent(detail)\""),
    ("detail?.result_content", "displayResultContent(detail)"),
    ("detail?.resultContent", "displayResultContent(detail)")
]
for old, new in replacements:
    s = s.replace(old, new)

VUE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(VUE))
