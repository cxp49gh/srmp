import unittest

from app.adaptive_planner import build_adaptive_tool_calls, plan_adaptive_tools
from app.schemas import MapAiAgentRequest, MapAiContext, ToolCall, ToolResult


class AdaptivePlannerTest(unittest.TestCase):
    def test_skips_when_evidence_is_sufficient(self):
        request = MapAiAgentRequest(message="分析区域", mapContext=MapAiContext(mode="REGION"))

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": True, "businessHitCount": 2, "knowledgeHitCount": 0},
            tool_results=[ToolResult(toolName="gis.queryRegionSummary", success=True, count=2)],
            existing_plan=[ToolCall(toolName="gis.queryRegionSummary", args={}, reason="first pass")],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("SKIPPED_SUFFICIENT", decision.status)
        self.assertEqual([], decision.added_calls)

    def test_adds_knowledge_when_business_has_no_hits_and_knowledge_not_run(self):
        request = MapAiAgentRequest(
            message="这个区域怎么养护",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026),
            options={"useKnowledge": False},
        )

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=[ToolResult(toolName="gis.queryRegionSummary", success=True, count=0)],
            existing_plan=[ToolCall(toolName="gis.queryRegionSummary", args={"routeCode": "G210"}, reason="first pass")],
        )

        self.assertTrue(decision.should_replan)
        self.assertEqual("PLANNED", decision.status)
        self.assertEqual(["knowledge.retrieve"], [item.toolName for item in decision.added_calls])
        self.assertIn("G210", decision.added_calls[0].args["query"])

    def test_does_not_repeat_executed_tools(self):
        request = MapAiAgentRequest(message="补充解释", mapContext=MapAiContext(mode="REGION"))

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=[
                ToolResult(toolName="gis.queryRegionSummary", success=True, count=0),
                ToolResult(toolName="knowledge.retrieve", success=True, count=0),
            ],
            existing_plan=[
                ToolCall(toolName="gis.queryRegionSummary", args={}, reason="first pass"),
                ToolCall(toolName="knowledge.retrieve", args={"query": "区域"}, reason="first pass"),
            ],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("SKIPPED_NO_CANDIDATE", decision.status)
        self.assertIn("knowledge.retrieve", decision.skipped_tool_names)

    def test_skips_when_tool_limit_reached(self):
        request = MapAiAgentRequest(message="分析区域", mapContext=MapAiContext(mode="REGION"))
        existing_results = [
            ToolResult(toolName="gis.queryRegionSummary", success=True, count=0),
            ToolResult(toolName="gis.queryDiseases", success=True, count=0),
            ToolResult(toolName="gis.queryAssessmentResults", success=True, count=0),
            ToolResult(toolName="gis.queryDiseasesByStakeRange", success=True, count=0),
            ToolResult(toolName="gis.queryNearbyObjects", success=True, count=0),
            ToolResult(toolName="template.match", success=True, count=0),
        ]

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=existing_results,
            existing_plan=[ToolCall(toolName=item.toolName, args={}, reason="first pass") for item in existing_results],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("SKIPPED_LIMIT", decision.status)

    def test_request_can_disable_adaptive_planning(self):
        request = MapAiAgentRequest(
            message="分析区域",
            mapContext=MapAiContext(mode="REGION"),
            options={"disableAdaptivePlanning": True},
        )

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=[],
            existing_plan=[],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("DISABLED", decision.status)

    def test_region_candidate_uses_region_summary_when_it_was_not_executed(self):
        request = MapAiAgentRequest(
            message="分析框选区域",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026, geometry={"type": "Polygon"}),
        )

        calls = build_adaptive_tool_calls(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            executed_tool_names={"knowledge.retrieve"},
        )

        self.assertEqual("gis.queryRegionSummary", calls[0].toolName)
        self.assertEqual("G210", calls[0].args["routeCode"])


if __name__ == "__main__":
    unittest.main()
