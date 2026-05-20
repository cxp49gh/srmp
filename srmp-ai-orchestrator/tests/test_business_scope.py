import unittest

from app.planner import context_args
from app.schemas import MapAiContext


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


if __name__ == "__main__":
    unittest.main()
