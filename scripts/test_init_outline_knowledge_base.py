#!/usr/bin/env python3
"""Quality checks for SRMP Outline seed knowledge.

The seed catalog is production-facing: these documents are pushed into Outline
and then synchronized into the AI knowledge base. The checks intentionally avoid
external dependencies so they can run in CI or on a target environment with only
Python available.
"""

import importlib.util
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "init-outline-knowledge-base.py"


def load_seed_module():
    spec = importlib.util.spec_from_file_location("init_outline_knowledge_base", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_metadata(markdown):
    marker = "## SRMP 元数据"
    start = markdown.find(marker)
    if start < 0:
        return {}
    first_fence = markdown.find("```", start)
    if first_fence < 0:
        return {}
    first_line_end = markdown.find("\n", first_fence)
    closing_fence = markdown.find("```", first_line_end + 1)
    if first_line_end < 0 or closing_fence < 0:
        return {}
    yaml_text = markdown[first_line_end + 1:closing_fence]
    result = {}
    current_key = None
    for raw in yaml_text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("- ") and current_key:
            result.setdefault(current_key, []).append(line[2:].strip())
            continue
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        if not value:
            result[key] = []
            current_key = key
        else:
            current_key = None
            if value == "true":
                result[key] = True
            elif value == "false":
                result[key] = False
            elif value == "[]":
                result[key] = []
            else:
                result[key] = value
    return result


class OutlineSeedKnowledgeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = load_seed_module()
        cls.catalog = cls.module.build_catalog()
        cls.by_title = {item["title"]: item for item in cls.catalog}
        cls.metadata = {item["title"]: parse_metadata(item["text"]) for item in cls.catalog}

    def test_catalog_keeps_required_outline_tree(self):
        required_roots = [
            "00_知识库治理",
            "10_指标与评定标准",
            "20_病害识别与分级",
            "30_养护处置工艺",
            "40_方案模板蓝本",
            "50_项目资料",
            "60_案例库",
            "70_术语与问答",
        ]
        for title in required_roots:
            self.assertIn(title, self.by_title)
            self.assertIsNone(self.by_title[title]["parent"])

        expected_children = {
            "10_指标与评定标准": ["MQI 指标解释", "PCI 指标解释", "RDI 指标解释"],
            "20_病害识别与分级": ["裂缝类病害", "坑槽类病害", "车辙类病害"],
            "30_养护处置工艺": ["裂缝处置工艺", "坑槽修补工艺", "铣刨重铺"],
            "50_项目资料": ["项目背景资料", "区域养护策略", "历史检测资料", "数据口径说明"],
            "60_案例库": ["优秀方案案例", "典型病害处置案例", "问题复盘"],
            "70_术语与问答": ["指标 FAQ", "养护工艺 FAQ", "平台使用 FAQ"],
        }
        for parent, children in expected_children.items():
            actual = [item["title"] for item in self.catalog if item.get("parent") == parent]
            for child in children:
                self.assertIn(child, actual)

    def test_rag_documents_do_not_ship_placeholder_sample_language(self):
        forbidden = [
            "样例：",
            "初始化样例",
            "应替换为正式规范",
            "## 样例内容",
        ]
        offenders = []
        for item in self.catalog:
            meta = self.metadata[item["title"]]
            if meta.get("ragEnabled") is not True:
                continue
            text = item["text"]
            for phrase in forbidden:
                if phrase in text:
                    offenders.append("%s contains %s" % (item["title"], phrase))
        self.assertEqual([], offenders)

    def test_business_rag_documents_have_metadata_and_retrieval_keywords(self):
        required_fields = ["srmpKnowledgeType", "domains", "objectTypes", "solutionTypes", "capabilityIds", "ragEnabled", "status"]
        for item in self.catalog:
            meta = self.metadata[item["title"]]
            if meta.get("ragEnabled") is not True or meta.get("srmpKnowledgeType") == "CATEGORY":
                continue
            for field in required_fields:
                self.assertIn(field, meta, item["title"])
            text = item["text"]
            self.assertIn("### 知识要点", text, item["title"])
            self.assertIn("### 可直接参考的实例卡片", text, item["title"])
            self.assertIn("AI 使用方式", text, item["title"])
            self.assertIn("不建议输出", text, item["title"])

        corpus = "\n".join(item["text"] for item in self.catalog)
        for keyword in ["PCI", "裂缝宽度", "坑槽", "车辙连续", "灌缝", "铣刨重铺", "现场复核", "来源上下文"]:
            self.assertIn(keyword, corpus)

    def test_template_blueprints_are_not_indexed_by_rag(self):
        for title in ["路线养护报告", "路段养护计划", "病害处置建议", "病害复核意见", "评定养护建议", "区域养护建议"]:
            meta = self.metadata[title]
            self.assertEqual("TEMPLATE_BLUEPRINT", meta.get("srmpKnowledgeType"))
            self.assertIs(False, meta.get("ragEnabled"))


if __name__ == "__main__":
    unittest.main()
