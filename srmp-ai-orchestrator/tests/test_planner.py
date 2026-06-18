import unittest

from app.planner import plan_tools
from app.schemas import MapAiAgentRequest, MapAiContext


def request_with_followup(source):
    return MapAiAgentRequest(
        message="继续解释该来源",
        mapContext=MapAiContext(
            mode="OBJECT",
            routeCode="CURRENT",
            mapObject={
                "objectType": "ROAD_SECTION",
                "objectId": "section-current",
                "routeCode": "CURRENT",
                "startStake": 10,
                "endStake": 12,
            },
            extra={
                "rawContext": {
                    "query": {
                        "projectId": "project-1",
                        "sectionTier": "LINE",
                    }
                },
                "followupSource": source,
            },
        ),
        options={"useKnowledge": True, "topK": 4},
    )


class SourceFollowupPlanningTest(unittest.TestCase):
    def test_plain_reference_followup_uses_only_knowledge(self):
        request = request_with_followup({
            "sourceId": "chunk-1",
            "sourceType": "KNOWLEDGE",
            "sourceTitle": "PCI 指标说明",
            "contentExcerpt": "PCI 用于评价路面损坏状况。",
            "bindingType": "NONE",
            "bindingStatus": "VALID",
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")

        self.assertEqual(["knowledge.retrieve"], [call.toolName for call in calls])
        self.assertIn("PCI 指标说明", calls[0].args["query"])
        self.assertIn("PCI 用于评价路面损坏状况", calls[0].args["query"])

    def test_not_found_object_followup_does_not_trigger_gis_tools(self):
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "sourceTitle": "已删除病害",
            "contentExcerpt": "历史病害记录",
            "bindingType": "OBJECT",
            "bindingStatus": "NOT_FOUND",
            "mapTarget": {
                "objectType": "DISEASE",
                "objectId": "deleted-1",
                "routeCode": "Y016140727",
            },
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")

        self.assertEqual(["knowledge.retrieve"], [call.toolName for call in calls])

    def test_unknown_binding_status_does_not_trigger_gis_tools(self):
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "bindingType": "OBJECT",
            "bindingStatus": "LEGACY",
            "mapTarget": {
                "objectType": "DISEASE",
                "objectId": "disease-1",
            },
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")

        self.assertEqual(["knowledge.retrieve"], [call.toolName for call in calls])

    def test_disease_object_followup_uses_source_target(self):
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "sourceTitle": "裂缝",
            "bindingType": "OBJECT",
            "bindingStatus": "UNVERIFIED",
            "mapTarget": {
                "objectType": "DISEASE",
                "objectId": "disease-1",
                "routeCode": "Y016140727",
                "startStake": 1.2,
                "endStake": 1.25,
            },
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")

        self.assertEqual(
            ["gis.queryNearbyObjects", "knowledge.retrieve"],
            [call.toolName for call in calls],
        )
        nearby = calls[0]
        self.assertEqual("disease-1", nearby.args["objectId"])
        self.assertEqual("Y016140727", nearby.args["routeCode"])
        self.assertEqual(1.2, nearby.args["stakeStart"])
        self.assertNotEqual("section-current", nearby.args["objectId"])
        self.assertEqual("project-1", nearby.args["projectId"])

    def test_assessment_object_followup_uses_assessment_and_stake_tools(self):
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "bindingType": "OBJECT",
            "bindingStatus": "VALID",
            "mapTarget": {
                "objectType": "ASSESSMENT_RESULT",
                "objectId": "assessment-1",
                "routeCode": "Y016140727",
                "startStake": 0,
                "endStake": 0.1,
            },
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")
        names = [call.toolName for call in calls]

        self.assertEqual(
            [
                "gis.queryAssessmentResults",
                "gis.queryDiseasesByStakeRange",
                "knowledge.retrieve",
            ],
            names,
        )
        for call in calls[:2]:
            self.assertEqual("assessment-1", call.args["objectId"])
            self.assertEqual(0, call.args["stakeStart"])
            self.assertEqual(0.1, call.args["stakeEnd"])

    def test_assessment_object_without_stakes_does_not_use_range_tool(self):
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "bindingType": "OBJECT",
            "bindingStatus": "VALID",
            "mapTarget": {
                "objectType": "ASSESSMENT_RESULT",
                "objectId": "assessment-1",
                "routeCode": "Y016140727",
            },
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")

        self.assertEqual(
            ["gis.queryAssessmentResults", "knowledge.retrieve"],
            [call.toolName for call in calls],
        )

    def test_route_stake_range_followup_uses_range_tools(self):
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "bindingType": "RANGE",
            "bindingStatus": "UNVERIFIED",
            "mapTarget": {
                "routeCode": "Y016140727",
                "startStake": 2,
                "endStake": 3,
            },
        })

        calls = plan_tools(request, "OBJECT_ANALYSIS")

        self.assertEqual(
            [
                "gis.queryAssessmentResults",
                "gis.queryDiseasesByStakeRange",
                "knowledge.retrieve",
            ],
            [call.toolName for call in calls],
        )
        for call in calls[:2]:
            self.assertEqual("Y016140727", call.args["routeCode"])
            self.assertEqual(2, call.args["stakeStart"])
            self.assertEqual(3, call.args["stakeEnd"])

    def test_geometry_range_followup_uses_region_summary(self):
        geometry = {
            "type": "Polygon",
            "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.1]]],
        }
        request = request_with_followup({
            "sourceType": "BUSINESS_DATA",
            "bindingType": "RANGE",
            "bindingStatus": "VALID",
            "mapTarget": {
                "objectType": "MAP_REGION",
                "geometry": geometry,
            },
        })

        calls = plan_tools(request, "REGION_ANALYSIS")

        self.assertEqual(
            ["gis.queryRegionSummary", "knowledge.retrieve"],
            [call.toolName for call in calls],
        )
        self.assertEqual(geometry, calls[0].args["geometry"])

    def test_direct_map_context_followup_source_is_supported(self):
        request = MapAiAgentRequest(
            message="解释来源",
            mapContext=MapAiContext.model_validate({
                "mode": "ROUTE",
                "routeCode": "CURRENT",
                "followupSource": {
                    "sourceType": "KNOWLEDGE",
                    "bindingType": "NONE",
                    "bindingStatus": "VALID",
                },
            }),
        )

        calls = plan_tools(request, "ROUTE_ANALYSIS")

        self.assertEqual(["knowledge.retrieve"], [call.toolName for call in calls])


if __name__ == "__main__":
    unittest.main()
