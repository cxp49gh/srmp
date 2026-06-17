import importlib.util
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "init-outline-knowledge-base.py"


def load_script_module():
    spec = importlib.util.spec_from_file_location("init_outline_knowledge_base", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def catalog_by_title():
    module = load_script_module()
    return {item["title"]: item for item in module.build_catalog()}


class OutlineKnowledgeCatalogTest(unittest.TestCase):
    def test_non_template_knowledge_docs_explain_how_to_add_instances(self):
        catalog = catalog_by_title()
        excluded_types = ("srmpKnowledgeType: CATEGORY", "srmpKnowledgeType: TEMPLATE_BLUEPRINT")

        knowledge_docs = [
            item
            for item in catalog.values()
            if not any(marker in item["text"] for marker in excluded_types)
        ]

        self.assertTrue(knowledge_docs)
        for item in knowledge_docs:
            text = item["text"]
            self.assertIn("## 新增实例格式", text, item["title"])
            self.assertIn("- 实例 ID：", text, item["title"])
            self.assertIn("- 适用对象：", text, item["title"])
            self.assertIn("- AI 使用方式：", text, item["title"])
            self.assertIn("### 实例：", text, item["title"])
            self.assertIn("- 不建议输出：", text, item["title"])
            self.assertIn("### 可直接参考的实例卡片", text, item["title"])

    def test_governance_contains_instance_authoring_guide(self):
        catalog = catalog_by_title()

        guide = catalog["如何新增知识实例"]
        self.assertEqual("00_知识库治理", guide["parent"])
        self.assertIn("复制实例卡片", guide["text"])
        self.assertIn("实例 ID", guide["text"])
        self.assertIn("同步后抽查", guide["text"])

    def test_template_blueprints_remain_non_rag_import_sources(self):
        catalog = catalog_by_title()
        template_titles = [
            "路线养护报告",
            "路段养护计划",
            "病害处置建议",
            "病害复核意见",
            "评定养护建议",
            "区域养护建议",
        ]

        for title in template_titles:
            text = catalog[title]["text"]
            self.assertIn("srmpKnowledgeType: TEMPLATE_BLUEPRINT", text)
            self.assertIn("templateImportEnabled: true", text)
            self.assertIn("ragEnabled: false", text)


if __name__ == "__main__":
    unittest.main()
