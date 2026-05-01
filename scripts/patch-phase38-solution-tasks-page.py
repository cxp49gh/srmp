#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
VUE = ROOT / "srmp-web-ui/src/views/agent/SolutionTasksPage.vue"
def fail(msg):
    print("[FAIL] " + msg); sys.exit(1)
if not VUE.exists(): fail("文件不存在：" + str(VUE))
bak = VUE.with_suffix(VUE.suffix + ".phase38.bak")
if not bak.exists(): bak.write_text(VUE.read_text(encoding="utf-8"), encoding="utf-8")
s = VUE.read_text(encoding="utf-8")
if "getSolutionTaskAiContext" not in s:
    s = s.replace("getSolutionMarkdownExportUrl,",
                  "getSolutionMarkdownExportUrl,\n  getSolutionMarkdownV2ExportUrl,\n  getSolutionTaskAiContext,\n  getSolutionTaskStatusTimeline,\n  restoreSolutionTaskVersion,")
if "const aiContext = ref" not in s:
    s = s.replace("const versions = ref<Record<string, any>[]>([])",
                  "const versions = ref<Record<string, any>[]>([])\nconst aiContext = ref<Record<string, any> | null>(null)\nconst statusTimeline = ref<Record<string, any>[]>([])")
if "aiContext.value = await getSolutionTaskAiContext" not in s:
    s = s.replace("sources.value = await getSolutionTaskSources(item.id)\n  quality.value = await getSolutionQualityResult(item.id)",
                  "sources.value = await getSolutionTaskSources(item.id)\n  quality.value = await getSolutionQualityResult(item.id)\n  aiContext.value = await getSolutionTaskAiContext(item.id)\n  statusTimeline.value = await getSolutionTaskStatusTimeline(item.id)")
if "AI 分析依据" not in s:
    s = s.replace("<SolutionQualityPanel :quality=\"quality\" />",
                  """<SolutionQualityPanel :quality="quality" />

          <el-card v-if="aiContext && (aiContext.aiAnswer || aiContext.generationMode)" shadow="never" class="mb">
            <template #header>AI 分析依据</template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="生成模式">{{ aiContext.generationMode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Trace">{{ aiContext.aiTraceId || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="aiContext.aiAnswer" class="ai-answer-preview">{{ aiContext.aiAnswer }}</div>
          </el-card>""")
if "状态时间线" not in s:
    s = s.replace("<template #header>引用来源</template>",
                  """<template #header>引用来源</template>
        <div v-if="statusTimeline.length" class="timeline-box">
          <strong>状态时间线</strong>
          <div v-for="item in statusTimeline" :key="item.id" class="timeline-item">
            {{ item.from_status || '-' }} -> {{ item.to_status }} | {{ item.action || '-' }}
          </div>
        </div>""")
if "restoreVersion(" not in s:
    s = s.replace("<p>{{ item.change_note || '版本快照' }}</p>",
                  """<p>{{ item.change_note || '版本快照' }}</p>
        <el-button v-if="detail?.draft_status === 'DRAFT'" size="small" @click="restoreVersion(item.version_no)">恢复到该版本</el-button>""")
    s = s.replace("""function exportMarkdown() {
  if (!detail.value?.id) return
  window.open(getSolutionMarkdownExportUrl(detail.value.id), '_blank')
}""",
                  """function exportMarkdown() {
  if (!detail.value?.id) return
  window.open(getSolutionMarkdownV2ExportUrl(detail.value.id), '_blank')
}

async function restoreVersion(versionNo: number) {
  if (!detail.value?.id) return
  detail.value = await restoreSolutionTaskVersion(detail.value.id, versionNo, `恢复到 v${versionNo}`)
  versions.value = await getSolutionTaskVersions(detail.value.id)
  ElMessage.success('已恢复历史版本')
}""")
if ".ai-answer-preview" not in s:
    s = s.replace("</style>", """
.ai-answer-preview {
  margin-top: 10px;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.timeline-box {
  margin-bottom: 12px;
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
}

.timeline-item {
  margin-top: 6px;
  color: #475569;
}
</style>""")
VUE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(VUE))
