#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

if not FILE.exists():
    fail(f"文件不存在：{FILE}")

bak = FILE.with_suffix(FILE.suffix + ".phase38-5.bak")
if not bak.exists():
    bak.write_text(FILE.read_text(encoding="utf-8"), encoding="utf-8")

s = FILE.read_text(encoding="utf-8")

remove_fields = [
    r'\s*@Resource\s+private\s+MapObjectDiseaseAdviceEnhancer\s+mapObjectDiseaseAdviceEnhancer;\s*',
    r'\s*@Resource\s+private\s+AssessmentResultAdviceEnhancer\s+assessmentResultAdviceEnhancer;\s*',
    r'\s*@Resource\s+private\s+RoadAssetAdviceEnhancer\s+roadAssetAdviceEnhancer;\s*',
    r'\s*@Resource\s+private\s+MapAiAnswerPolisher\s+mapAiAnswerPolisher;\s*',
]
for pattern in remove_fields:
    s = re.sub(pattern, '\n', s)

s = re.sub(
    r'\n\s*private\s+List\s+planTools\s*\([^)]*\)\s*\{.*?\n\s*\}\s*(?=\n\s*private\s+boolean\s+isExplicitSolutionDraftRequest\s*\()',
    '\n',
    s,
    flags=re.S
)

s = re.sub(
    r'\n\s*private\s+boolean\s+useKnowledge\s*\([^)]*\)\s*\{.*?\n\s*\}\s*(?=\n\s*private\s+Map<[^>]+>\s+defaultArgs\s*\()',
    '\n',
    s,
    flags=re.S
)

required = [
    "private MapAiToolPlanner mapAiToolPlanner;",
    "private MapAiAnswerEnhancerRegistry mapAiAnswerEnhancerRegistry;",
    "mapAiToolPlanner.plan(",
    "mapAiAnswerEnhancerRegistry.enhance("
]
missing = [item for item in required if item not in s]
if missing:
    fail("MapAiAgentServiceImpl 缺少必要策略接入：" + ", ".join(missing))

for forbidden in [
    "private List planTools(",
    "private boolean useKnowledge(",
    "private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer",
    "private AssessmentResultAdviceEnhancer assessmentResultAdviceEnhancer",
    "private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer",
    "private MapAiAnswerPolisher mapAiAnswerPolisher"
]:
    if forbidden in s:
        fail("MapAiAgentServiceImpl 仍存在旧逻辑：" + forbidden)

FILE.write_text(s, encoding="utf-8")
print("[OK] patched " + str(FILE))
