#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MAP_SERVICE = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java"
RAG_EVAL_PAGE = ROOT / "srmp-web-ui/src/views/agent/RagEvalPage.vue"

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    bak = path.with_suffix(path.suffix + ".phase37-2-3.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def patch_map_service():
    backup(MAP_SERVICE)
    s = MAP_SERVICE.read_text(encoding="utf-8")

    old = '''    private boolean isCrackScenario(String message, MapAiContext context, List<AiKnowledgeSearchHit> sources) {
        if (containsAny(message, "裂缝", "开裂", "灌缝", "封缝")) {
            return true;
        }
        if (context != null && context.getMapObject() != null) {
            String diseaseName = firstString(context.getMapObject(), "diseaseName", "disease_name", "diseaseType", "disease_type");
            if (containsAny(diseaseName, "裂缝", "开裂")) {
                return true;
            }
        }
        if (sources != null) {
            for (AiKnowledgeSearchHit hit : sources) {
                if (hit == null) continue;
                if (containsAny(hit.getTitle(), "裂缝") || containsAny(hit.getSectionTitle(), "裂缝") || containsAny(hit.getContent(), "裂缝", "灌缝", "封层", "渗水")) {
                    return true;
                }
            }
        }
        return false;
    }
'''
    new = '''    private boolean isCrackScenario(String message, MapAiContext context, List<AiKnowledgeSearchHit> sources) {
        // Phase37.2.3：
        // 裂缝术语兜底只能由“用户问题”或“当前地图对象”触发，不能仅因为 sources 中混入裂缝指南就触发。
        // 否则修补损坏、坑槽等问题在 topK 命中裂缝指南时，会被误追加“开槽灌缝/封层/渗水”等裂缝建议。
        if (containsAny(message, "裂缝", "开裂", "灌缝", "封缝")) {
            return true;
        }
        if (context != null && context.getMapObject() != null) {
            String diseaseName = firstString(context.getMapObject(), "diseaseName", "disease_name", "diseaseType", "disease_type");
            String objectType = firstString(context.getMapObject(), "objectType", "object_type", "type");
            if (containsAny(diseaseName, "裂缝", "开裂") || containsAny(objectType, "CRACK")) {
                return true;
            }
        }
        return false;
    }
'''
    if old in s:
        s = s.replace(old, new, 1)
    elif "private boolean isCrackScenario" in s and "不能仅因为 sources 中混入裂缝指南就触发" not in s:
        fail("找到 isCrackScenario，但函数体与预期不一致，请手动检查后应用")
    else:
        print("[WARN] MapAiAgentServiceImpl 未找到待替换 isCrackScenario，可能已修复")

    MAP_SERVICE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(MAP_SERVICE))

def patch_rag_eval_page():
    backup(RAG_EVAL_PAGE)
    s = RAG_EVAL_PAGE.read_text(encoding="utf-8")

    changed = False
    replacements = [
        ('<div v-if="row.answerPreview" class="answer-preview">回答摘要：{{ row.answerPreview }}</div>',
         '<div v-if="row.passed === false && row.answerPreview" class="answer-preview">回答摘要：{{ row.answerPreview }}</div>'),
        ('<div v-if="row.answerPreview" class="answer-preview">',
         '<div v-if="row.passed === false && row.answerPreview" class="answer-preview">')
    ]
    for old, new in replacements:
        if old in s:
            s = s.replace(old, new)
            changed = True

    if not changed and "answerPreview" in s and "row.passed === false && row.answerPreview" not in s:
        fail("RagEvalPage.vue 中存在 answerPreview，但未找到可自动替换的展示片段")

    RAG_EVAL_PAGE.write_text(s, encoding="utf-8")
    print("[OK] patched " + str(RAG_EVAL_PAGE))

def verify():
    text = MAP_SERVICE.read_text(encoding="utf-8")
    if "不能仅因为 sources 中混入裂缝指南就触发" not in text:
        fail("MapAiAgentServiceImpl 裂缝误判修复未生效")
    page = RAG_EVAL_PAGE.read_text(encoding="utf-8")
    if "answerPreview" in page and "row.passed === false && row.answerPreview" not in page:
        fail("RagEvalPage answerPreview 仍可能在通过用例错误列展示")
    print("[OK] Phase37.2.3 修复检查通过")

patch_map_service()
patch_rag_eval_page()
verify()
