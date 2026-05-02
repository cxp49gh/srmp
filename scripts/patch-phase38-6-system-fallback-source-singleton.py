#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[1]
def backup(path: Path):
    if not path.exists(): print('[WARN] not found:', path); return False
    bak = path.with_suffix(path.suffix + '.phase38-6.bak')
    if not bak.exists(): bak.write_text(path.read_text(encoding='utf-8'), encoding='utf-8')
    return True

def patch_fallback_support():
    file = ROOT / 'srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/support/AiSolutionFallbackTemplateSupport.java'
    if not backup(file): return
    s = file.read_text(encoding='utf-8')
    s = re.sub(r'\s*md\.append\("## 六、引用来源\\n\\n"\);\s*md\.append\("- 系统兜底模板\\n"\);\s*return md\.toString\(\);', '\n        return AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(md.toString());', s, flags=re.S)
    s = re.sub(r'\s*md\.append\("## 六、引用来源\\n\\n"\);\s*md\.append\("- 系统兜底模板\\n"\);\s*', '\n', s, flags=re.S)
    if 'stripEmbeddedFallbackSourceSection(md.toString())' not in s:
        s = s.replace('return md.toString();', 'return AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(md.toString());', 1)
    if 'content = AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(content);' not in s:
        s = re.sub(r'(public static String repairFallbackContentIfNeeded\([^)]*\) \{)', r'\1\n        content = AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(content);', s, count=1, flags=re.S)
    if 'AiSolutionFallbackSourceGuard.dedupeSources(result)' not in s:
        s = s.replace('return result;\n    }\n\n    public static Map', 'return AiSolutionFallbackSourceGuard.dedupeSources(result);\n    }\n\n    public static Map', 1)
    file.write_text(s, encoding='utf-8'); print('[OK] patched', file)

def patch_closure_service():
    file = ROOT / 'srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTaskClosureServiceImpl.java'
    if not backup(file): return
    s = file.read_text(encoding='utf-8')
    if 'AiSolutionFallbackSourceGuard' not in s:
        s = s.replace('import com.smartroad.srmp.agent.solution.service.AiSolutionTaskClosureService;\n', 'import com.smartroad.srmp.agent.solution.service.AiSolutionTaskClosureService;\nimport com.smartroad.srmp.agent.solution.support.AiSolutionFallbackSourceGuard;\n')
    if 'stripEmbeddedFallbackSourceSection(safe(task.get("result_content"))' not in s:
        s = s.replace('md.append("## 三、方案正文\\n\\n").append(safe(task.get("result_content"))).append("\\n\\n");', 'String resultContent = AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(safe(task.get("result_content")));\n        md.append("## 三、方案正文\\n\\n").append(resultContent).append("\\n\\n");')
    s = re.sub(r'return namedParameterJdbcTemplate\.queryForList\(\s*"select \* from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",\s*new MapSqlParameterSource\(\)\.addValue\("tenantId", tenantId\)\.addValue\("taskId", taskId\)\s*\);', 'List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(\n                "select * from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",\n                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("taskId", taskId));\n        return AiSolutionFallbackSourceGuard.dedupeSources(rows);', s, count=1, flags=re.S)
    file.write_text(s, encoding='utf-8'); print('[OK] patched', file)

def patch_solution_tasks_page():
    file = ROOT / 'srmp-web-ui/src/views/agent/SolutionTasksPage.vue'
    if not backup(file): return
    s = file.read_text(encoding='utf-8')
    if 'function isSystemFallbackSource' not in s:
        helper = """
function isSystemFallbackSource(source: any) {
  const title = String(source?.sourceTitle || source?.source_title || source?.title || '')
  const type = String(source?.sourceType || source?.source_type || source?.type || '')
  return type === 'SYSTEM_TEMPLATE' || title.includes('系统兜底模板') || title.includes('兜底模板') || title.includes('Fallback Template')
}
function dedupeSolutionSources(list: any[] = []) {
  const seen = new Set<string>()
  const result: any[] = []
  list.forEach((item: any) => {
    if (!item) return
    const key = isSystemFallbackSource(item) ? 'SYSTEM_TEMPLATE|SYSTEM_FALLBACK_TEMPLATE' : `${item.sourceType || item.source_type || item.type || ''}|${item.sourceId || item.source_id || item.id || ''}|${item.sourceTitle || item.source_title || item.title || ''}`
    if (!seen.has(key)) { seen.add(key); result.push(item) }
  })
  return result
}
function stripEmbeddedFallbackSourceSection(content: string) {
  if (!content) return content
  return content.replace(/\n*##\s*(六、)?引用来源\s*\n\s*[-*]\s*系统兜底模板\s*$/s, '').trim()
}
"""
        marker='function exportMarkdown()'
        s = s.replace(marker, helper+'\n'+marker, 1) if marker in s else s+'\n'+helper
    s = s.replace('sources.value = await getSolutionTaskSources(item.id)', 'sources.value = dedupeSolutionSources(await getSolutionTaskSources(item.id))')
    if 'return hasEmptyFallbackField ? buildFallbackMarkdownForDisplay(task) : content' in s:
        s = s.replace('return hasEmptyFallbackField ? buildFallbackMarkdownForDisplay(task) : content', 'return stripEmbeddedFallbackSourceSection(hasEmptyFallbackField ? buildFallbackMarkdownForDisplay(task) : content)')
    else:
        s = s.replace('{{ detail.result_content }}', '{{ stripEmbeddedFallbackSourceSection(detail.result_content) }}')
        s = s.replace('{{ detail.resultContent }}', '{{ stripEmbeddedFallbackSourceSection(detail.resultContent) }}')
    file.write_text(s, encoding='utf-8'); print('[OK] patched', file)
patch_fallback_support(); patch_closure_service(); patch_solution_tasks_page(); print('[OK] Phase38.6 patches applied')
