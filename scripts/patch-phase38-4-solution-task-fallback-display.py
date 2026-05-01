#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTaskClosureServiceImpl.java"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    bak = path.with_suffix(path.suffix + ".phase38-4.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

backup(SERVICE)
s = SERVICE.read_text(encoding="utf-8")

if "AiSolutionFallbackTemplateSupport" not in s:
    s = s.replace(
        "import com.smartroad.srmp.agent.solution.service.AiSolutionTaskClosureService;\n",
        "import com.smartroad.srmp.agent.solution.service.AiSolutionTaskClosureService;\n"
        "import com.smartroad.srmp.agent.solution.support.AiSolutionFallbackTemplateSupport;\n"
    )

# exportMarkdownV2 中把 result_content 修复后再导出
old = '        md.append("## 三、方案正文\\n\\n").append(safe(task.get("result_content"))).append("\\n\\n");'
new = '''        String fixedResultContent = AiSolutionFallbackTemplateSupport.repairFallbackContentIfNeeded(
                safe(task.get("result_content")),
                task,
                safe(ai.get("aiAnswer"))
        );
        md.append("## 三、方案正文\\n\\n").append(fixedResultContent).append("\\n\\n");'''
if old in s:
    s = s.replace(old, new, 1)
elif "repairFallbackContentIfNeeded" not in s:
    print("[WARN] 未定位 exportMarkdownV2 result_content 拼接点，请手动合并 repairFallbackContentIfNeeded")

SERVICE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(SERVICE))
