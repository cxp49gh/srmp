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
    bak = path.with_suffix(path.suffix + ".phase37-5-1.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def patch_service():
    backup(MAP_SERVICE)
    s = MAP_SERVICE.read_text(encoding="utf-8")

    if "com.smartroad.srmp.agent.mapagent.enhance.MapAiAnswerEnhanceContext" not in s:
        s = s.replace(
            "import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;\n",
            "import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;\n"
            "import com.smartroad.srmp.agent.mapagent.enhance.MapAiAnswerEnhanceContext;\n"
            "import com.smartroad.srmp.agent.mapagent.enhance.MapAiAnswerEnhancerRegistry;\n"
        )

    if "private MapAiAnswerEnhancerRegistry mapAiAnswerEnhancerRegistry;" not in s:
        marker = "    @Resource\n    private AiTraceService aiTraceService;\n"
        if marker not in s:
            fail("无法定位 AiTraceService 注入点")
        s = s.replace(marker, marker + "\n    @Resource\n    private MapAiAnswerEnhancerRegistry mapAiAnswerEnhancerRegistry;\n", 1)

    if "mapAiAnswerEnhancerRegistry.enhance" not in s:
        old = "answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);"
        new = """answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);
            answer = mapAiAnswerEnhancerRegistry.enhance(
                    answer,
                    MapAiAnswerEnhanceContext.of(answer, message, context, knowledgeSources, toolResults)
            );"""
        if old not in s:
            fail("无法定位 enrichAnswerWithKnowledgeTerms 调用点")
        s = s.replace(old, new, 1)

    MAP_SERVICE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(MAP_SERVICE))

def verify():
    s = MAP_SERVICE.read_text(encoding="utf-8")
    checks = [
        "MapAiAnswerEnhancerRegistry",
        "MapAiAnswerEnhanceContext",
        "mapAiAnswerEnhancerRegistry.enhance"
    ]
    missing = [c for c in checks if c not in s]
    if missing:
        fail("Phase37.5.1 service patch 未完整生效，缺失：" + ", ".join(missing))
    print("[OK] Phase37.5.1 service patch verify passed")

patch_service()
verify()
