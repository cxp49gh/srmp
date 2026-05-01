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
    bak = path.with_suffix(path.suffix + ".phase37-5.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def patch():
    backup(MAP_SERVICE)
    s = MAP_SERVICE.read_text(encoding="utf-8")

    # 1. imports
    if "com.smartroad.srmp.agent.mapagent.enhance.MapAiAnswerEnhanceContext" not in s:
        s = s.replace(
            "import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;\n",
            "import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;\n"
            "import com.smartroad.srmp.agent.mapagent.enhance.MapAiAnswerEnhanceContext;\n"
            "import com.smartroad.srmp.agent.mapagent.enhance.MapAiAnswerEnhancerRegistry;\n"
            "import com.smartroad.srmp.agent.mapagent.plan.MapAiToolPlanner;\n"
        )

    # 2. inject planner + enhancer registry
    if "private MapAiToolPlanner mapAiToolPlanner;" not in s:
        marker = "    @Resource\n    private AiTraceService aiTraceService;\n"
        if marker not in s:
            fail("无法定位 AiTraceService 注入点")
        s = s.replace(
            marker,
            marker + "\n    @Resource\n    private MapAiToolPlanner mapAiToolPlanner;\n\n    @Resource\n    private MapAiAnswerEnhancerRegistry mapAiAnswerEnhancerRegistry;\n",
            1
        )

    # 3. replace tool planning call
    old = "List<String> toolNames = planTools(intent, context, request == null ? null : request.getOptions(), message);"
    new = "List<String> toolNames = mapAiToolPlanner.plan(intent, context, request == null ? null : request.getOptions(), message);"
    if old in s:
        s = s.replace(old, new, 1)
    elif new not in s:
        fail("无法定位 toolNames 规划调用")

    # 4. replace direct answer enhancer chain
    old_chain = '''            answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);
            answer = mapObjectDiseaseAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);
            answer = assessmentResultAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);
            answer = roadAssetAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);
            answer = mapAiAnswerPolisher.polish(answer);'''
    new_chain = '''            answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);
            answer = mapAiAnswerEnhancerRegistry.enhance(
                    answer,
                    MapAiAnswerEnhanceContext.of(answer, message, context, knowledgeSources, toolResults)
            );'''
    if old_chain in s:
        s = s.replace(old_chain, new_chain, 1)
    elif "mapAiAnswerEnhancerRegistry.enhance" not in s:
        # fallback: inject after enrichAnswerWithKnowledgeTerms and leave old calls in place disabled by not touching? Safer to fail.
        fail("无法定位直接 answer enhancer 调用链，请手动接入 MapAiAnswerEnhancerRegistry")

    MAP_SERVICE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(MAP_SERVICE))

def verify():
    s = MAP_SERVICE.read_text(encoding="utf-8")
    checks = [
        "MapAiToolPlanner",
        "MapAiAnswerEnhancerRegistry",
        "mapAiToolPlanner.plan",
        "mapAiAnswerEnhancerRegistry.enhance",
        "MapAiAnswerEnhanceContext.of"
    ]
    missing = [c for c in checks if c not in s]
    if missing:
        fail("Phase37.5 strategy patch 未完整生效，缺失：" + ", ".join(missing))
    print("[OK] Phase37.5 strategy patch verify passed")

patch()
verify()
