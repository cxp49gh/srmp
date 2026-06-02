import unittest

from app.governance import resolve_capability
from app.governance import governance_plan_simulate_for_payload
from app.intent import recognize_intent
from app.planner import plan_tools
from app.schemas import MapAiAgentRequest, MapAiContext


class GovernancePlanningTest(unittest.TestCase):
    def test_metric_question_uses_governance_policy_tools_only(self):
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True, "topK": 3},
        )
        fallback_intent, detail = recognize_intent(request)
        capability = resolve_capability(request, fallback_intent, detail)

        calls = plan_tools(request, capability["intent"], detail, capability=capability)

        self.assertEqual("knowledge.metric_explain", capability["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item.toolName for item in calls])

    def test_route_analysis_uses_policy_business_tools_and_knowledge(self):
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True},
        )
        fallback_intent, detail = recognize_intent(request)
        capability = resolve_capability(request, fallback_intent, detail)

        calls = plan_tools(request, capability["intent"], detail, capability=capability)
        names = [item.toolName for item in calls]

        self.assertEqual("map.route_analysis", capability["capabilityId"])
        self.assertIn("gis.queryRegionSummary", names)
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("knowledge.retrieve", names)

    def test_route_report_plan_includes_draft_tool_for_policy_check(self):
        result = governance_plan_simulate_for_payload({
            "action": "GENERATE_ROUTE_REPORT",
            "message": "生成当前路线养护报告",
            "mapContext": {
                "mode": "ROUTE",
                "routeCode": "Y016140727",
                "year": 2026,
            },
            "options": {"useKnowledge": True},
        })
        names = [item["toolName"] for item in result["toolPlan"]]

        self.assertEqual("solution.generate", result["capabilityId"])
        self.assertEqual("PASS", result["policyStatus"])
        self.assertIn("gis.queryRegionSummary", names)
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("knowledge.retrieve", names)
        self.assertIn("solution.generateDraft", names)

    def test_metric_question_with_explicit_route_context_uses_route_policy(self):
        request = MapAiAgentRequest(
            message="结合当前路线解释 PCI 为什么低",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True},
        )
        fallback_intent, detail = recognize_intent(request)
        capability = resolve_capability(request, fallback_intent, detail)

        calls = plan_tools(request, capability["intent"], detail, capability=capability)
        names = [item.toolName for item in calls]

        self.assertEqual("map.route_analysis", capability["capabilityId"])
        self.assertIn("gis.queryRegionSummary", names)
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("knowledge.retrieve", names)


if __name__ == "__main__":
    unittest.main()
