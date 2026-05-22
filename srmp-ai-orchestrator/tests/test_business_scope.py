import unittest

from app.intent import recognize_intent
from app.planner import context_args, plan_tools
from app.schemas import MapAiAgentRequest, MapAiContext


class BusinessScopePlanningTest(unittest.TestCase):
    def test_context_args_carry_project_section_object_and_stake_scope(self):
        ctx = MapAiContext(
            tenantId="tenant-a",
            mode="OBJECT",
            routeCode="Y016140727",
            year=2026,
            selectedLayers=["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"],
            mapObject={
                "objectType": "ASSESSMENT_RESULT",
                "objectId": "assessment-1",
                "startStake": 0,
                "endStake": 14.072,
                "raw": {
                    "object_type": "ROAD_SECTION_LEDGER",
                    "direction": "UP",
                },
            },
            extra={
                "rawContext": {
                    "query": {
                        "projectId": "project-2026",
                        "sectionTier": "LEDGER",
                    }
                }
            },
        )

        args = context_args(ctx, {"limit": 20})

        self.assertEqual("tenant-a", args["tenantId"])
        self.assertEqual("project-2026", args["projectId"])
        self.assertEqual("LEDGER", args["sectionTier"])
        self.assertEqual("OBJECT", args["contextScope"])
        self.assertEqual("ASSESSMENT_RESULT", args["objectType"])
        self.assertEqual("assessment-1", args["objectId"])
        self.assertEqual("ROAD_SECTION_LEDGER", args["assessmentObjectType"])
        self.assertEqual("UP", args["direction"])
        self.assertEqual(0, args["stakeStart"])
        self.assertEqual(14.072, args["stakeEnd"])
        self.assertEqual(["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"], args["selectedLayers"])

    def test_object_context_args_prefer_selected_object_route_over_query_route(self):
        ctx = MapAiContext(
            tenantId="tenant-a",
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
            extra={
                "rawContext": {
                    "query": {
                        "routeCode": "G210",
                        "projectId": "project-2026",
                    }
                }
            },
        )

        args = context_args(ctx, {"limit": 20})

        self.assertEqual("Y016140727", args["routeCode"])

    def test_analyze_route_action_plans_route_business_tools(self):
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线整体路况和养护重点",
            mapContext=MapAiContext(
                tenantId="tenant-a",
                mode="ROUTE",
                routeCode="Y016140727",
                year=2026,
                selectedLayers=["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"],
                extra={
                    "rawContext": {
                        "query": {
                            "projectId": "project-2026",
                            "sectionTier": "LINE",
                        }
                    }
                },
            ),
            options={"action": "ANALYZE_ROUTE", "useKnowledge": True, "topK": 5},
        )

        intent, detail = recognize_intent(request)
        calls = plan_tools(request, intent, detail)
        names = [call.toolName for call in calls]

        self.assertEqual("ROUTE_ANALYSIS", intent)
        self.assertIn("gis.queryRegionSummary", names)
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("knowledge.retrieve", names)

    def test_route_report_does_not_duplicate_route_object_queries(self):
        request = MapAiAgentRequest(
            message="生成路线养护报告",
            mapContext=MapAiContext(
                tenantId="tenant-a",
                mode="OBJECT",
                routeCode="Y016140727",
                year=2026,
                mapObject={
                    "objectType": "ROAD_ROUTE",
                    "routeCode": "Y016140727",
                    "year": 2026,
                },
            ),
            options={"action": "GENERATE_ROUTE_REPORT", "useKnowledge": False},
        )

        calls = plan_tools(request, "SOLUTION_GENERATE", {})
        names = [call.toolName for call in calls]

        self.assertEqual(1, names.count("gis.queryRegionSummary"))
        self.assertEqual(1, names.count("gis.queryAssessmentResults"))
        self.assertEqual(1, names.count("gis.queryDiseases"))


if __name__ == "__main__":
    unittest.main()
