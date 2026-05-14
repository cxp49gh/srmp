import unittest

from app.map_agent_run import MapAgentRunWorkflow
from app.plan_execution import build_plan_execution, derive_actual_source_types, normalize_plan_preview
from app.schemas import MapAgentRunRequest, MapAiAgentResponse


class PlanExecutionHelpersTest(unittest.TestCase):
    def test_no_plan_returns_no_plan_status(self):
        plan = normalize_plan_preview(None)

        result = build_plan_execution(
            plan,
            {
                "runTraceId": "run-1",
                "actualAction": "ANALYZE_REGION",
                "actualIntent": "REGION_ANALYSIS",
                "toolResults": [],
                "sources": [],
                "evidence": {},
            },
        )

        self.assertFalse(plan["available"])
        self.assertFalse(result["available"])
        self.assertEqual("NO_PLAN", result["status"])
        self.assertEqual("run-1", result["runTraceId"])

    def test_matching_action_intent_and_tools_returns_matched(self):
        plan = normalize_plan_preview(
            {
                "planTraceId": "plan-1",
                "action": "ANALYZE_REGION",
                "intent": "REGION_ANALYSIS",
                "toolNames": ["gis.queryRegionSummary", "knowledge.retrieve"],
                "sourceTypes": ["BUSINESS_DATA", "KNOWLEDGE"],
            }
        )

        result = build_plan_execution(
            plan,
            {
                "runTraceId": "run-1",
                "actualAction": "ANALYZE_REGION",
                "actualIntent": "REGION_ANALYSIS",
                "toolResults": [
                    {"toolName": "gis.queryRegionSummary", "success": True},
                    {"toolName": "knowledge.retrieve", "success": True},
                ],
                "sources": [{"sourceType": "KNOWLEDGE"}],
                "evidence": {"businessHitCount": 4, "knowledgeHitCount": 2},
            },
        )

        self.assertTrue(result["available"])
        self.assertEqual("MATCHED", result["status"])
        self.assertEqual([], result["missingToolNames"])
        self.assertEqual([], result["extraToolNames"])
        self.assertEqual([], result["missingSourceTypes"])

    def test_missing_planned_tool_diverges(self):
        plan = normalize_plan_preview(
            {
                "planTraceId": "plan-2",
                "action": "ANALYZE_REGION",
                "intent": "REGION_ANALYSIS",
                "toolNames": ["gis.queryRegionSummary", "knowledge.retrieve"],
            }
        )

        result = build_plan_execution(
            plan,
            {
                "runTraceId": "run-2",
                "actualAction": "ANALYZE_REGION",
                "actualIntent": "REGION_ANALYSIS",
                "toolResults": [{"toolName": "gis.queryRegionSummary", "success": True}],
                "sources": [],
                "evidence": {},
            },
        )

        self.assertEqual("DIVERGED", result["status"])
        self.assertEqual(["knowledge.retrieve"], result["missingToolNames"])
        self.assertTrue(any(item["code"] == "PLANNED_TOOL_MISSING" for item in result["warnings"]))

    def test_extra_actual_tool_is_partial(self):
        plan = normalize_plan_preview(
            {
                "action": "ANALYZE_OBJECT",
                "intent": "OBJECT_ANALYSIS",
                "toolNames": ["gis.queryNearbyObjects"],
            }
        )

        result = build_plan_execution(
            plan,
            {
                "runTraceId": "run-3",
                "actualAction": "ANALYZE_OBJECT",
                "actualIntent": "OBJECT_ANALYSIS",
                "toolResults": [
                    {"toolName": "gis.queryNearbyObjects", "success": True},
                    {"toolName": "knowledge.retrieve", "success": True},
                ],
                "sources": [],
                "evidence": {},
            },
        )

        self.assertEqual("PARTIAL", result["status"])
        self.assertEqual(["knowledge.retrieve"], result["extraToolNames"])

    def test_adaptive_extra_tool_is_explained_as_partial(self):
        plan = normalize_plan_preview(
            {
                "planTraceId": "plan-adaptive-1",
                "action": "ANALYZE_REGION",
                "intent": "REGION_ANALYSIS",
                "toolNames": ["gis.queryRegionSummary"],
                "sourceTypes": ["BUSINESS_DATA"],
            }
        )

        result = build_plan_execution(
            plan,
            {
                "runTraceId": "run-adaptive-1",
                "actualAction": "ANALYZE_REGION",
                "actualIntent": "REGION_ANALYSIS",
                "toolResults": [
                    {"toolName": "gis.queryRegionSummary", "success": True},
                    {"toolName": "knowledge.retrieve", "success": True},
                ],
                "sources": [],
                "evidence": {"businessHitCount": 0, "knowledgeHitCount": 2},
                "adaptivePlanning": {
                    "status": "EXECUTED",
                    "reason": "业务工具未命中，追加知识检索补充解释依据。",
                    "addedToolNames": ["knowledge.retrieve"],
                },
            },
        )

        self.assertEqual("PARTIAL", result["status"])
        self.assertEqual(["knowledge.retrieve"], result["extraToolNames"])
        self.assertEqual(["knowledge.retrieve"], result["adaptiveExtraToolNames"])
        self.assertEqual("业务工具未命中，追加知识检索补充解释依据。", result["adaptiveReason"])
        self.assertTrue(any(item["code"] == "ADAPTIVE_EXTRA_TOOL_EXECUTED" for item in result["warnings"]))

    def test_derives_actual_source_types_from_tools_sources_and_evidence(self):
        result = derive_actual_source_types(
            tool_results=[
                {"toolName": "gis.queryAssessmentResults", "success": True},
                {"toolName": "template.match", "success": True},
            ],
            sources=[{"sourceType": "MAP_OBJECT"}],
            evidence={"knowledgeHitCount": 3},
        )

        self.assertEqual(["BUSINESS_DATA", "KNOWLEDGE", "MAP_OBJECT", "TEMPLATE"], result)


class PlanExecutionRunWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_map_agent_run_response_contains_plan_execution(self):
        workflow = MapAgentRunWorkflow(base_workflow=FakeBaseWorkflow(), gateway=FakeGateway())
        request = MapAgentRunRequest(
            action="ANALYZE_REGION",
            message="分析当前区域",
            options={
                "traceId": "run-from-plan-1",
                "planPreview": {
                    "planTraceId": "plan-region-1",
                    "action": "ANALYZE_REGION",
                    "intent": "REGION_ANALYSIS",
                    "toolNames": ["gis.queryRegionSummary"],
                    "sourceTypes": ["BUSINESS_DATA"],
                },
            },
        )

        response = await workflow.run(request=request, tenant_id="default", trace_id=None)
        plan_execution = response.planExecution

        self.assertEqual("MATCHED", plan_execution["status"])
        self.assertEqual("plan-region-1", plan_execution["planTraceId"])
        self.assertEqual("run-from-plan-1", plan_execution["runTraceId"])
        self.assertEqual(plan_execution, response.data["planExecution"])
        self.assertEqual("MATCHED", response.answerMeta["planExecutionStatus"])
        self.assertEqual(plan_execution, response.trace["planExecution"])


class FakeBaseWorkflow:
    async def run(self, request, tenant_id, trace_id):
        return MapAiAgentResponse(
            answer="区域分析完成",
            intent="REGION_ANALYSIS",
            toolResults=[{"toolName": "gis.queryRegionSummary", "success": True, "count": 3}],
            sources=[],
            knowledgeSources=[],
            trace={"traceId": "run-from-plan-1"},
            data={
                "answerMeta": {"answerSource": "FALLBACK", "llmSuccess": False},
                "evidence": {"businessHitCount": 3, "knowledgeHitCount": 0},
            },
        )


class FakeGateway:
    async def execute_tool(self, *args, **kwargs):
        raise AssertionError("analysis run should delegate to FakeBaseWorkflow, not gateway")


if __name__ == "__main__":
    unittest.main()
