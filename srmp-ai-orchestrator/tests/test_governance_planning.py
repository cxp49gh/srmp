import unittest

from app.governance import resolve_capability
from app.governance import governance_plan_simulate_for_payload
from app.intent import recognize_intent
from app.planner import plan_tools
from app.schemas import MapAiAgentRequest, MapAiContext


class GovernancePlanningTest(unittest.TestCase):
    def _planned(self, request):
        fallback_intent, detail = recognize_intent(request)
        capability = resolve_capability(request, fallback_intent, detail)
        calls = plan_tools(request, capability["intent"], detail, capability=capability)
        return capability, calls

    def _call(self, calls, tool_name):
        for item in calls:
            if item.toolName == tool_name:
                return item
        self.fail(f"missing planned tool: {tool_name}")

    def test_metric_question_uses_governance_policy_tools_only(self):
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True, "topK": 3},
        )
        capability, calls = self._planned(request)

        self.assertEqual("knowledge.metric_explain", capability["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item.toolName for item in calls])

    def test_route_analysis_uses_policy_business_tools_and_knowledge(self):
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True},
        )
        capability, calls = self._planned(request)
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
            "actionInput": {"solutionType": "ROUTE_REPORT", "routeCode": "Y016140727"},
            "options": {"useKnowledge": True},
        })
        names = [item["toolName"] for item in result["toolPlan"]]

        self.assertEqual("solution.route_report", result["capabilityId"])
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
        capability, calls = self._planned(request)
        names = [item.toolName for item in calls]

        self.assertEqual("map.route_analysis", capability["capabilityId"])
        self.assertIn("gis.queryRegionSummary", names)
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("knowledge.retrieve", names)

    def test_disease_object_analysis_uses_nearby_objects_without_region_summary(self):
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前病害",
            mapContext=MapAiContext(
                mode="OBJECT",
                routeCode="G210",
                year=2026,
                mapObject={
                    "objectType": "DISEASE",
                    "objectId": "disease-1",
                    "routeCode": "Y016140727",
                    "stakeStart": 10.2,
                    "stakeEnd": 10.3,
                },
            ),
            options={"useKnowledge": True},
        )
        capability, calls = self._planned(request)
        names = [item.toolName for item in calls]
        nearby = self._call(calls, "gis.queryNearbyObjects")

        self.assertEqual("map.disease_analysis", capability["capabilityId"])
        self.assertIn("gis.queryNearbyObjects", names)
        self.assertIn("knowledge.retrieve", names)
        self.assertNotIn("gis.queryRegionSummary", names)
        self.assertEqual("Y016140727", nearby.args["routeCode"])

    def test_assessment_object_analysis_uses_stake_scoped_assessment_and_diseases(self):
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前评定结果",
            mapContext=MapAiContext(
                mode="OBJECT",
                routeCode="G210",
                year=2026,
                mapObject={
                    "objectType": "ASSESSMENT_RESULT",
                    "objectId": "assessment-1",
                    "routeCode": "Y016140727",
                    "startStake": 0,
                    "endStake": 14.072,
                },
            ),
            options={"useKnowledge": True},
        )
        capability, calls = self._planned(request)
        names = [item.toolName for item in calls]

        self.assertEqual("map.assessment_analysis", capability["capabilityId"])
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseasesByStakeRange", names)
        self.assertIn("knowledge.retrieve", names)
        self.assertNotIn("gis.queryRegionSummary", names)
        for tool_name in ("gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange"):
            planned = self._call(calls, tool_name)
            self.assertEqual("Y016140727", planned.args["routeCode"])
            self.assertEqual(0, planned.args["stakeStart"])
            self.assertEqual(14.072, planned.args["stakeEnd"])

    def test_road_section_analysis_uses_route_and_stake_scoped_business_tools(self):
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前路段",
            mapContext=MapAiContext(
                mode="OBJECT",
                routeCode="G210",
                year=2026,
                mapObject={
                    "objectType": "ROAD_SECTION",
                    "objectId": "section-1",
                    "routeCode": "Y016140727",
                    "startStake": 10,
                    "endStake": 12.5,
                },
            ),
            options={"useKnowledge": True},
        )
        capability, calls = self._planned(request)
        names = [item.toolName for item in calls]

        self.assertEqual("map.section_analysis", capability["capabilityId"])
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("gis.queryDiseasesByStakeRange", names)
        self.assertIn("knowledge.retrieve", names)
        self.assertNotIn("gis.queryRegionSummary", names)
        for tool_name in ("gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange"):
            planned = self._call(calls, tool_name)
            self.assertEqual("Y016140727", planned.args["routeCode"])
            self.assertEqual(10, planned.args["stakeStart"])
            self.assertEqual(12.5, planned.args["stakeEnd"])

    def test_region_analysis_uses_geometry_summary_and_knowledge(self):
        geometry = {
            "type": "Polygon",
            "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.1]]],
        }
        request = MapAiAgentRequest(
            action="ANALYZE_REGION",
            message="分析当前区域",
            mapContext=MapAiContext(mode="REGION", routeCode="Y016140727", year=2026, geometry=geometry),
            options={"useKnowledge": True},
        )
        capability, calls = self._planned(request)
        names = [item.toolName for item in calls]
        summary = self._call(calls, "gis.queryRegionSummary")

        self.assertEqual("map.region_analysis", capability["capabilityId"])
        self.assertEqual(["gis.queryRegionSummary", "knowledge.retrieve"], names)
        self.assertEqual(geometry, summary.args["geometry"])

    def test_object_solution_plan_keeps_selected_object_route_for_evidence_and_draft(self):
        result = governance_plan_simulate_for_payload({
            "action": "GENERATE_OBJECT_SOLUTION",
            "message": "生成当前对象的结构化养护建议",
            "mapContext": {
                "mode": "OBJECT",
                "routeCode": "G210",
                "year": 2026,
                "mapObject": {
                    "objectType": "ROAD_SECTION",
                    "objectId": "section-1",
                    "routeCode": "Y016140727",
                    "startStake": 10,
                    "endStake": 12.5,
                },
            },
            "actionInput": {
                "solutionType": "SECTION_PLAN",
                "routeCode": "Y016140727",
                "startStake": 10,
                "endStake": 12.5,
            },
            "options": {"useKnowledge": True},
        })
        calls = {item["toolName"]: item for item in result["toolPlan"]}

        self.assertEqual("solution.section_plan", result["capabilityId"])
        self.assertEqual("PASS", result["policyStatus"])
        self.assertIn("gis.queryAssessmentResults", calls)
        self.assertIn("gis.queryDiseases", calls)
        self.assertIn("gis.queryDiseasesByStakeRange", calls)
        self.assertIn("knowledge.retrieve", calls)
        self.assertIn("solution.generateDraft", calls)
        for tool_name in ("gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "solution.generateDraft"):
            args = calls[tool_name]["args"]
            self.assertEqual("Y016140727", args["routeCode"])
            self.assertEqual(10, args["stakeStart"])
            self.assertEqual(12.5, args["stakeEnd"])


if __name__ == "__main__":
    unittest.main()
