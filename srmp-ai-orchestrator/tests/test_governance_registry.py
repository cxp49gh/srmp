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


if __name__ == "__main__":
    unittest.main()
