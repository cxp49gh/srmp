import unittest

from app.governance import governance_registry, resolve_capability
from app.schemas import MapAiAgentRequest, MapAiContext


class GovernanceRegistryTest(unittest.TestCase):
    def test_registry_loads_capabilities_and_tools(self):
        registry = governance_registry()

        self.assertTrue(registry.config_valid)
        self.assertGreaterEqual(len(registry.capabilities), 6)
        self.assertIn("knowledge.metric_explain", registry.capability_by_id)
        self.assertIn("knowledge.retrieve", registry.tool_by_name)

    def test_metric_explanation_matches_knowledge_only_capability(self):
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
        )

        match = resolve_capability(request, fallback_intent="ROUTE_ANALYSIS", intent_detail={})

        self.assertEqual("knowledge.metric_explain", match["capabilityId"])
        self.assertEqual("KNOWLEDGE_QA", match["intent"])
        self.assertEqual("KNOWLEDGE_ONLY", match["contextUsage"])
        self.assertIn("gis.queryRegionSummary", match["toolPolicy"]["prohibited"])
        self.assertTrue(any("keyword:PCI" == item for item in match["matchedRules"]))

    def test_explicit_route_action_matches_route_analysis(self):
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"action": "ANALYZE_ROUTE"},
        )

        match = resolve_capability(request, fallback_intent="ROUTE_ANALYSIS", intent_detail={"action": "ANALYZE_ROUTE"})

        self.assertEqual("map.route_analysis", match["capabilityId"])
        self.assertEqual("ROUTE_ANALYSIS", match["intent"])
        self.assertIn("gis.queryRegionSummary", match["toolPolicy"]["required"])

    def test_route_report_matches_concrete_solution_capability(self):
        request = MapAiAgentRequest(
            action="GENERATE_ROUTE_REPORT",
            message="生成当前路线养护报告",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            actionInput={"solutionType": "ROUTE_REPORT", "routeCode": "Y016140727"},
            options={"action": "GENERATE_ROUTE_REPORT"},
        )

        match = resolve_capability(
            request,
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": "GENERATE_ROUTE_REPORT"},
        )

        self.assertEqual("solution.route_report", match["capabilityId"])
        self.assertEqual("solution.generate", match["family"])
        self.assertEqual("solution_generate", match["pipeline"])
        self.assertEqual("ROUTE_REPORT", match["solutionType"])
        self.assertEqual("route_report_default", match["templateExpectation"]["templateCode"])

    def test_disease_review_and_treatment_are_separate_solution_capabilities(self):
        base_context = MapAiContext(
            mode="OBJECT",
            routeCode="Y016140727",
            year=2026,
            mapObject={"objectType": "DISEASE", "objectId": "disease-1", "routeCode": "Y016140727"},
        )
        review = resolve_capability(
            MapAiAgentRequest(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前病害复核意见",
                mapContext=base_context,
                actionInput={"solutionType": "DISEASE_REVIEW"},
                options={"action": "GENERATE_OBJECT_SOLUTION"},
            ),
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": "GENERATE_OBJECT_SOLUTION", "objectType": "DISEASE"},
        )
        treatment = resolve_capability(
            MapAiAgentRequest(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前病害处置建议",
                mapContext=base_context,
                actionInput={"solutionType": "DISEASE_TREATMENT"},
                options={"action": "GENERATE_OBJECT_SOLUTION"},
            ),
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": "GENERATE_OBJECT_SOLUTION", "objectType": "DISEASE"},
        )

        self.assertEqual("solution.disease_review", review["capabilityId"])
        self.assertEqual("solution.disease_treatment", treatment["capabilityId"])


if __name__ == "__main__":
    unittest.main()
