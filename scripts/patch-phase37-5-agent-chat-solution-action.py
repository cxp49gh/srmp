#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
VUE = ROOT / "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    bak = path.with_suffix(path.suffix + ".phase37-5.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def patch():
    backup(VUE)
    s = VUE.read_text(encoding="utf-8")

    # 1. Add message action after evidence panel
    if "class=\"assistant-action-row\"" not in s:
        marker = '<AiEvidencePanel v-if="item.role === \'assistant\'" :message="item" :map-context="props.context" />'
        if marker not in s:
            fail("无法定位 AiEvidencePanel")
        insert = marker + '''
          <div v-if="item.role === 'assistant' && activeMapObject" class="assistant-action-row">
            <el-button
              size="small"
              type="success"
              plain
              :loading="solutionLoading"
              :disabled="loading || solutionLoading"
              @click="generateDefaultSolutionDraft"
            >
              基于本次分析生成方案草稿
            </el-button>
          </div>'''
        s = s.replace(marker, insert, 1)

    # 2. Add method
    if "function generateDefaultSolutionDraft()" not in s:
        marker = "async function generateSolutionDraft(solutionType: MapObjectSolutionType) {"
        if marker not in s:
            fail("无法定位 generateSolutionDraft")
        method = '''function generateDefaultSolutionDraft() {
  const primary = solutionActions.value.find((it: any) => it.primary) || solutionActions.value[0]
  if (!primary) {
    ElMessage.warning('当前对象暂不支持生成方案草稿')
    return
  }
  generateSolutionDraft(primary.type)
}

'''
        s = s.replace(marker, method + marker, 1)

    # 3. Add css
    if ".assistant-action-row" not in s:
        marker = ".trace-button {"
        style = '''.assistant-action-row {
  margin-top: 8px;
  display: flex;
  justify-content: flex-start;
}

'''
        if marker in s:
            s = s.replace(marker, style + marker, 1)
        else:
            s = s.replace("</style>", style + "\n</style>", 1)

    VUE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(VUE))

def verify():
    s = VUE.read_text(encoding="utf-8")
    checks = ["assistant-action-row", "generateDefaultSolutionDraft", "基于本次分析生成方案草稿"]
    missing = [c for c in checks if c not in s]
    if missing:
        fail("AgentChatFloat.vue patch 未完整生效，缺失：" + ", ".join(missing))
    print("[OK] Phase37.5 frontend patch verify passed")

patch()
verify()
