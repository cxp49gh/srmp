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
    bak = path.with_suffix(path.suffix + ".phase38-2.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

backup(FILE)
s = FILE.read_text(encoding="utf-8")

old = '''            String answer = llmClient.chat(systemPrompt(), prompt);
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

new = '''            String answer = llmClient.chat(systemPrompt(), prompt);
            Map<String, Object> llmDiag = llmClient.diagnostics();

            boolean retriedWithCompactPrompt = false;
            Map<String, Object> firstDiag = new LinkedHashMap<>(llmDiag);
            if ((answer == null || answer.trim().isEmpty()) && llmClient.enabled()) {
                String compactPrompt = buildCompactLlmPrompt(message, context, toolResults, knowledgeSources);
                answer = llmClient.chat(systemPrompt(), compactPrompt);
                retriedWithCompactPrompt = true;
                llmDiag = llmClient.diagnostics();
            }

            if (answer == null || answer.trim().isEmpty()) {
                answer = localAnswer(message, context, toolResults, knowledgeSources);
                Map<String, Object> llmData = new LinkedHashMap<>(llmDiag);
                llmData.put("reason", "LLM 未返回内容，使用本地 Agent 回答");
                llmData.put("retriedWithCompactPrompt", retriedWithCompactPrompt);
                llmData.put("firstAttempt", firstDiag);
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
                llmData.put("retriedWithCompactPrompt", retriedWithCompactPrompt);
                llmData.put("firstAttempt", firstDiag);
                llmTimer.success(1, llmData);
            }'''

if old not in s:
    if "buildCompactLlmPrompt" in s:
        print("[OK] MapAiAgentServiceImpl 已包含 compact retry 逻辑")
    else:
        fail("无法定位 llm_answer diagnostics 逻辑，请确认已应用 Phase38.1 后再执行")
else:
    s = s.replace(old, new, 1)

if "private String buildCompactLlmPrompt" not in s:
    marker = "    private String buildPrompt(String message, MapAiContext context, List<AiToolResult> toolResults, List<AiKnowledgeSearchHit> sources) {"
    if marker not in s:
        fail("无法定位 buildPrompt 方法")
    method = r'''
    private String buildCompactLlmPrompt(String message,
                                         MapAiContext context,
                                         List<AiToolResult> toolResults,
                                         List<AiKnowledgeSearchHit> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于当前地图对象和参考资料，直接输出结构化养护分析。\\n");
        sb.append("要求：必须包含 主要问题、成因判断、现场复核重点、养护处置建议、优先级。\\n\\n");
        sb.append("【用户问题】\\n").append(message == null ? "" : message).append("\\n\\n");

        sb.append("【地图上下文】\\n");
        if (context != null) {
            sb.append("mode=").append(context.getMode()).append("\\n");
            sb.append("routeCode=").append(context.getRouteCode()).append("\\n");
            sb.append("year=").append(context.getYear()).append("\\n");
            sb.append("mapObject=").append(context.getMapObject()).append("\\n\\n");
        }

        sb.append("【参考资料 Top3】\\n");
        if (sources != null) {
            int i = 1;
            for (AiKnowledgeSearchHit source : sources) {
                if (source == null) {
                    continue;
                }
                sb.append(i).append(". ").append(source.getTitle());
                if (source.getSectionTitle() != null) {
                    sb.append(" / ").append(source.getSectionTitle());
                }
                sb.append("\\n");
                String content = source.getContent();
                if (content != null) {
                    sb.append(content.length() > 600 ? content.substring(0, 600) : content);
                }
                sb.append("\\n\\n");
                i++;
                if (i > 3) {
                    break;
                }
            }
        }

        sb.append("【工具摘要】\\n");
        if (toolResults != null) {
            for (AiToolResult tool : toolResults) {
                if (tool == null) {
                    continue;
                }
                sb.append("- ").append(tool.getToolName()).append(": ").append(tool.getSummary()).append("\\n");
            }
        }

        sb.append("\\n请不要返回空内容。若资料不足，也要基于当前地图对象给出保守建议。\\n");
        return sb.toString();
    }

'''
    s = s.replace(marker, method + marker, 1)

FILE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(FILE))
