#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MAP_SERVICE = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    bak = path.with_suffix(path.suffix + ".phase37-4-2.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def patch_map_ai_agent_service():
    backup(MAP_SERVICE)
    s = MAP_SERVICE.read_text(encoding="utf-8")

    # 1. 注入 enhancer bean
    if "private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer;" not in s:
        marker = "    @Resource\n    private AiTraceService aiTraceService;\n"
        if marker not in s:
            fail("无法定位 AiTraceService 字段，请手动注入 MapObjectDiseaseAdviceEnhancer")
        s = s.replace(marker, marker + "\n    @Resource\n    private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer;\n", 1)

    # 2. 回答清洗后增加专病种回答增强
    old = "            answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);\n            cleanTimer.success"
    new = "            answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);\n            answer = mapObjectDiseaseAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);\n            cleanTimer.success"
    if old in s and "mapObjectDiseaseAdviceEnhancer.enhance" not in s:
        s = s.replace(old, new, 1)
    elif "mapObjectDiseaseAdviceEnhancer.enhance" not in s:
        fail("无法定位 enrichAnswerWithKnowledgeTerms 调用，请手动增加 enhancer.enhance")

    # 3. planTools 增加 message 参数
    s = s.replace(
        "List<String> toolNames = planTools(intent, context, request == null ? null : request.getOptions());",
        "List<String> toolNames = planTools(intent, context, request == null ? null : request.getOptions(), message);"
    )
    s = s.replace(
        "private List<String> planTools(MapAiIntent intent, MapAiContext context, Map<String, Object> options) {",
        "private List<String> planTools(MapAiIntent intent, MapAiContext context, Map<String, Object> options, String message) {"
    )

    # 4. 意图识别：只有明确“方案草稿/保存方案/生成方案”才归为 SOLUTION_GENERATE
    old_intent = '        if (containsAny(text, "生成方案", "处置建议", "报告草稿", "养护建议")) return MapAiIntent.SOLUTION_GENERATE;\n'
    new_intent = '        if (isExplicitSolutionDraftRequest(text)) return MapAiIntent.SOLUTION_GENERATE;\n'
    if old_intent in s:
        s = s.replace(old_intent, new_intent, 1)

    # 5. 工具规划：普通“处置建议”不自动调用 solution.generateDraft
    old_tool = '        if (intent == MapAiIntent.SOLUTION_GENERATE) tools.add("solution.generateDraft");\n'
    new_tool = '        if (intent == MapAiIntent.SOLUTION_GENERATE && isExplicitSolutionDraftRequest(message)) tools.add("solution.generateDraft");\n'
    if old_tool in s:
        s = s.replace(old_tool, new_tool, 1)

    # 6. 增加 explicit 判断方法
    if "private boolean isExplicitSolutionDraftRequest" not in s:
        marker = "    private boolean useKnowledge(Map<String, Object> options) {"
        if marker not in s:
            fail("无法定位 useKnowledge 方法，无法插入 isExplicitSolutionDraftRequest")
        method = '''    private boolean isExplicitSolutionDraftRequest(String message) {
        String text = message == null ? "" : message;
        return containsAny(text,
                "生成方案", "方案草稿", "生成草稿", "保存方案", "保存为方案", "保存为方案任务",
                "形成方案", "生成任务", "生成养护方案", "保存任务", "创建方案任务");
    }

'''
        s = s.replace(marker, method + marker, 1)

    # 7. Prompt 增强 Top1 资料吸收要求与专病种结构化要求
    if "必须优先吸收 Top1 参考资料" not in s:
        target = '        sb.append("8. 回答应尽量保留知识库中的专业术语，并在结尾列出参考资料标题。\\\\n");\n'
        insert = target + '        sb.append("9. 必须优先吸收 Top1 参考资料中的处置工艺、成因判断和复核要点，不能只输出泛化建议。\\\\n");\n        sb.append("10. 若当前对象为病害，应按：主要问题、成因判断、现场复核、处置建议、优先级、参考依据 组织回答。\\\\n");\n'
        if target in s:
            s = s.replace(target, insert, 1)
        else:
            print("[WARN] 未找到 Prompt 插入点，跳过 Prompt 增强")

    MAP_SERVICE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(MAP_SERVICE))

def verify():
    s = MAP_SERVICE.read_text(encoding="utf-8")
    checks = [
        "MapObjectDiseaseAdviceEnhancer",
        "mapObjectDiseaseAdviceEnhancer.enhance",
        "isExplicitSolutionDraftRequest",
        "必须优先吸收 Top1 参考资料"
    ]
    missing = [c for c in checks if c not in s]
    if missing:
        fail("MapAiAgentServiceImpl 修复未完整生效，缺失：" + ", ".join(missing))
    print("[OK] Phase37.4.2 patch verify passed")

patch_map_ai_agent_service()
verify()
