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
    bak = path.with_suffix(path.suffix + ".phase37-4-6.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def patch():
    backup(MAP_SERVICE)
    s = MAP_SERVICE.read_text(encoding="utf-8")

    # 1. 注入 RoadAssetAdviceEnhancer
    if "private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer;" not in s:
        if "private AssessmentResultAdviceEnhancer assessmentResultAdviceEnhancer;" in s:
            marker = "    private AssessmentResultAdviceEnhancer assessmentResultAdviceEnhancer;\n"
            s = s.replace(marker, marker + "\n    @Resource\n    private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer;\n", 1)
        elif "private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer;" in s:
            marker = "    private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer;\n"
            s = s.replace(marker, marker + "\n    @Resource\n    private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer;\n", 1)
        else:
            marker = "    @Resource\n    private AiTraceService aiTraceService;\n"
            if marker not in s:
                fail("无法定位字段注入点，请手动注入 RoadAssetAdviceEnhancer")
            s = s.replace(marker, marker + "\n    @Resource\n    private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer;\n", 1)

    # 2. answer 生成后调用 roadAssetAdviceEnhancer
    if "roadAssetAdviceEnhancer.enhance" not in s:
        if "answer = assessmentResultAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);" in s:
            s = s.replace(
                "answer = assessmentResultAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);",
                "answer = assessmentResultAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);\n            answer = roadAssetAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);",
                1
            )
        elif "answer = mapObjectDiseaseAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);" in s:
            s = s.replace(
                "answer = mapObjectDiseaseAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);",
                "answer = mapObjectDiseaseAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);\n            answer = roadAssetAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);",
                1
            )
        elif "answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);" in s:
            s = s.replace(
                "answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);",
                "answer = enrichAnswerWithKnowledgeTerms(answer, message, context, knowledgeSources);\n            answer = roadAssetAdviceEnhancer.enhance(answer, message, context, knowledgeSources, toolResults);",
                1
            )
        else:
            fail("无法定位 answer enhancer 插入点")

    # 3. planTools 为 ROAD_ROUTE / ROAD_SECTION 增加评定和病害工具
    if "ROAD_ROUTE".lower() not in s.lower() or "ROAD_SECTION".lower() not in s.lower():
        print("[WARN] 当前文件未出现 ROAD_ROUTE/ROAD_SECTION 规划逻辑，将尝试在 type 获取后插入")

    if 'tools.add("gis.queryDiseases");' not in s:
        # 插入在 if (OBJECT_ANALYSIS...) 块中的 type 判断之后
        needle = 'String type = firstString(context.getMapObject(), "objectType", "object_type", "type");'
        inject = '''String type = firstString(context.getMapObject(), "objectType", "object_type", "type");
            if ("ROAD_ROUTE".equalsIgnoreCase(type) || "ROUTE".equalsIgnoreCase(type)) {
                tools.add("gis.queryAssessmentResults");
                tools.add("gis.queryDiseases");
            }
            if ("ROAD_SECTION".equalsIgnoreCase(type) || "SECTION".equalsIgnoreCase(type) || "ROAD_SEGMENT".equalsIgnoreCase(type) || "SEGMENT".equalsIgnoreCase(type)) {
                tools.add("gis.queryAssessmentResults");
                tools.add("gis.queryDiseases");
                tools.add("gis.queryDiseasesByStakeRange");
            }'''
        if needle in s:
            s = s.replace(needle, inject, 1)
        else:
            fail("无法定位 type 获取语句，无法加入 ROAD_ROUTE/ROAD_SECTION 工具规划")

    # 4. Prompt 增加路线/路段对象专项要求
    if "若当前对象为路线或路段" not in s:
        if 'sb.append("11. 若当前对象为评定结果，应显式分析 MQI/PQI/PCI、grade、主要短板、可能成因和养护处置建议。\\\\n");' in s:
            s = s.replace(
                'sb.append("11. 若当前对象为评定结果，应显式分析 MQI/PQI/PCI、grade、主要短板、可能成因和养护处置建议。\\\\n");',
                'sb.append("11. 若当前对象为评定结果，应显式分析 MQI/PQI/PCI、grade、主要短板、可能成因和养护处置建议。\\\\n");\n        sb.append("12. 若当前对象为路线或路段，应结合评定结果、病害分布、低分区间和路段属性，输出路线/路段级养护建议。\\\\n");',
                1
            )
        else:
            marker = "return sb.toString();"
            if marker in s:
                s = s.replace(marker, 'sb.append("12. 若当前对象为路线或路段，应结合评定结果、病害分布、低分区间和路段属性，输出路线/路段级养护建议。\\\\n");\n        return sb.toString();', 1)

    MAP_SERVICE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(MAP_SERVICE))

def verify():
    s = MAP_SERVICE.read_text(encoding="utf-8")
    checks = [
        "RoadAssetAdviceEnhancer",
        "roadAssetAdviceEnhancer.enhance",
        "gis.queryDiseases",
        "ROAD_ROUTE",
        "ROAD_SECTION"
    ]
    missing = [c for c in checks if c not in s]
    if missing:
        fail("Phase37.4.6 patch 未完整生效，缺失：" + ", ".join(missing))
    print("[OK] Phase37.4.6 patch verify passed")

patch()
verify()
