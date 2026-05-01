#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
VUE = ROOT / "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
def fail(msg):
    print("[FAIL] " + msg); sys.exit(1)
if not VUE.exists(): fail("文件不存在：" + str(VUE))
bak = VUE.with_suffix(VUE.suffix + ".phase38.bak")
if not bak.exists(): bak.write_text(VUE.read_text(encoding="utf-8"), encoding="utf-8")
s = VUE.read_text(encoding="utf-8")
if "updateSolutionTaskAiContext" not in s:
    s = s.replace("import { saveMapObjectSolutionDraft } from '../../../api/solution'",
                  "import { saveMapObjectSolutionDraft, updateSolutionTaskAiContext } from '../../../api/solution'")
if "function latestAssistantMessage()" not in s:
    marker = "async function saveSolutionDraft() {"
    helper = """function latestAssistantMessage() {
  const list = messages.value.filter((it: any) => it.role === 'assistant')
  return list.length ? list[list.length - 1] : null
}

function buildAiEvidenceFromMessage(message: any) {
  const toolResults = message?.toolResults || []
  const knowledgeTool = toolResults.find((it: any) => (it.toolName || it.name) === 'knowledge.retrieve')
  const data = knowledgeTool?.data || {}
  return {
    traceId: message?.trace?.id || message?.trace?.traceId || '',
    retrievalStrategy: data.retrievalStrategy || '',
    searchMode: data.searchMode || '',
    vectorUsed: data.vectorUsed === true,
    fallback: data.fallback === true,
    rewrittenQuery: data.rewrittenQuery || '',
    topScore: data.topScore,
    sourceCount: (message?.sources || []).length,
    mapContext: props.context || {}
  }
}

"""
    if marker not in s: fail("无法定位 saveSolutionDraft")
    s = s.replace(marker, helper + marker, 1)
old = """    savedSolutionTask.value = await saveMapObjectSolutionDraft({
      solutionType: solution.solutionType,"""
if old in s and "const assistantMessage = latestAssistantMessage()" not in s:
    s = s.replace(old, """    const assistantMessage = latestAssistantMessage()
    savedSolutionTask.value = await saveMapObjectSolutionDraft({
      solutionType: solution.solutionType,""", 1)
marker = """    })
    ElMessage.success('方案草稿已保存')"""
insert = """    })
    if (savedSolutionTask.value?.id && assistantMessage) {
      await updateSolutionTaskAiContext(savedSolutionTask.value.id, {
        aiTraceId: String(assistantMessage.trace?.id || assistantMessage.trace?.traceId || ''),
        aiAnswer: assistantMessage.content || '',
        aiSources: assistantMessage.sources || [],
        aiToolResults: assistantMessage.toolResults || [],
        aiEvidence: buildAiEvidenceFromMessage(assistantMessage),
        aiContext: {
          mapContext: props.context || {},
          mapObject: obj,
          solutionType: solution.solutionType,
          solutionTitle: solution.title
        },
        generationMode: 'MAP_AI_ANALYSIS_TO_SOLUTION_DRAFT'
      })
    }
    ElMessage.success('方案草稿已保存')"""
if marker in s and "MAP_AI_ANALYSIS_TO_SOLUTION_DRAFT" not in s:
    s = s.replace(marker, insert, 1)
VUE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(VUE))
