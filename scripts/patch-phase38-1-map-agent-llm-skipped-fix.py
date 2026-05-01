#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    bak = path.with_suffix(path.suffix + ".phase38-1.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

backup(FILE)
s = FILE.read_text(encoding="utf-8")

old = '''            AiTraceContext.StepTimer llmTimer = trace.step("llm_answer", "大模型回答");
            String answer = llmClient.chat(systemPrompt(), prompt);
            if (answer == null || answer.trim().isEmpty()) {
                answer = localAnswer(message, context, toolResults, knowledgeSources);
                llmTimer.skipped(map("reason", "LLM 未启用或未返回内容，使用本地 Agent 回答"));
                trace.setFallback(true);
            } else {
                llmTimer.success();
            }'''

new = '''            AiTraceContext.StepTimer llmTimer = trace.step("llm_answer", "大模型回答");
            String answer = llmClient.chat(systemPrompt(), prompt);
            Map<String, Object> llmDiag = llmClient.diagnostics();
            if (answer == null || answer.trim().isEmpty()) {
                answer = localAnswer(message, context, toolResults, knowledgeSources);
                Map<String, Object> llmData = new LinkedHashMap<>(llmDiag);
                llmData.put("reason", "LLM 未返回内容，使用本地 Agent 回答");
                if (llmClient.enabled()) {
                    String errorMessage = String.valueOf(llmData.getOrDefault("errorMessage", "LLM 返回为空"));
                    llmTimer.failed(new RuntimeException(errorMessage), llmData);
                } else {
                    llmTimer.skipped(llmData);
                }
                trace.setFallback(true);
            } else {
                Map<String, Object> llmData = new LinkedHashMap<>(llmDiag);
                llmData.put("answerChars", answer.length());
                llmTimer.success(1, llmData);
            }'''

if old not in s:
    if "llmClient.diagnostics()" in s:
        print("[OK] MapAiAgentServiceImpl 已包含 LLM diagnostics 逻辑")
    else:
        fail("无法定位 llm_answer 旧逻辑，请手动合并")
else:
    s = s.replace(old, new, 1)

FILE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(FILE))
