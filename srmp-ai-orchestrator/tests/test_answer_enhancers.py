import unittest

from app.answer_enhancers import enhance_answer
from app.prompt import fallback_answer
from app.schemas import MapAiAgentRequest, MapAiContext, ToolResult


class AnswerEnhancerTest(unittest.TestCase):
    def test_route_advice_describes_evidence_without_raw_query_counts(self):
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前路线",
            mapContext=MapAiContext(
                mode="OBJECT",
                mapObject={"objectType": "ROAD_ROUTE", "routeCode": "C067140727"},
            ),
        )
        tool_results = [
            ToolResult(toolName="gis.queryAssessmentResults", success=True, count=20, data={"items": [{}] * 20}),
            ToolResult(toolName="gis.queryDiseases", success=True, count=50, data={"items": [{}] * 50}),
        ]

        answer = enhance_answer("【基于当前地图对象】\n完成分析。", request, "OBJECT_ANALYSIS", {}, tool_results, [])

        self.assertIn("已结合评定结果和病害记录", answer)
        self.assertNotIn("系统已查询", answer)
        self.assertNotIn("20 条", answer)
        self.assertNotIn("50 条", answer)

    def test_section_advice_uses_business_language_for_evidence(self):
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前路段",
            mapContext=MapAiContext(
                mode="OBJECT",
                mapObject={"objectType": "ROAD_SECTION", "routeCode": "G210", "startStake": "K0", "endStake": "K1"},
            ),
        )
        tool_results = [
            ToolResult(toolName="gis.queryAssessmentResults", success=True, count=2, data={"items": [{}, {}]}),
            ToolResult(toolName="gis.queryDiseases", success=True, count=3, data={"items": [{}, {}, {}]}),
        ]

        answer = enhance_answer("完成分析。", request, "OBJECT_ANALYSIS", {}, tool_results, [])

        self.assertIn("已结合评定结果和病害记录", answer)
        self.assertNotIn("系统已查询", answer)

    def test_fallback_answer_hides_internal_orchestration_version(self):
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前路线",
            mapContext=MapAiContext(
                mode="OBJECT",
                mapObject={"objectType": "ROAD_ROUTE", "routeCode": "C067140727"},
            ),
        )
        answer = fallback_answer(
            request,
            "OBJECT_ANALYSIS",
            {"mode": "OBJECT", "routeCode": "C067140727", "year": 2026},
            {},
            [ToolResult(toolName="gis.queryAssessmentResults", success=True, count=20)],
        )

        self.assertIn("已完成当前地图对象分析", answer)
        self.assertNotIn("phase50", answer)
        self.assertNotIn("strategy", answer.lower())
        self.assertNotIn("只读编排", answer)


if __name__ == "__main__":
    unittest.main()
