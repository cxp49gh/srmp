import json
import unittest
from unittest.mock import patch

from app.config import settings
from app.schemas import MapAiAgentRequest, MapAiContext, ToolCall, ToolResult
from app.workflow import LangGraphWorkflow, strategy_metadata


class AdaptiveWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_workflow_runs_adaptive_second_pass_before_answer(self):
        gateway = AdaptiveFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            action="ANALYZE_REGION",
            message="分析这个区域怎么养护",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026),
            options={"traceId": "adaptive-run-1", "useKnowledge": False},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual(["gis.queryRegionSummary", "knowledge.retrieve"], gateway.tool_names)
        self.assertEqual("EXECUTED", response.data["adaptivePlanning"]["status"])
        self.assertEqual(["knowledge.retrieve"], response.data["adaptivePlanning"]["addedToolNames"])
        self.assertEqual(False, response.data["adaptivePlanning"]["evidenceBefore"]["sufficient"])
        self.assertEqual(0, response.data["adaptivePlanning"]["evidenceBefore"]["knowledgeHitCount"])
        self.assertEqual(True, response.data["adaptivePlanning"]["evidenceAfter"]["sufficient"])
        self.assertEqual(2, response.data["adaptivePlanning"]["evidenceAfter"]["knowledgeHitCount"])
        self.assertEqual("EXECUTED", response.answerMeta["adaptivePlanningStatus"])
        self.assertEqual(response.data["adaptivePlanning"], response.trace["adaptivePlanning"])
        self.assertTrue(response.data["evidence"]["sufficient"])
        self.assertIn("adaptive_tool_planning", response.data["nodeFlow"])

    async def test_workflow_skips_adaptive_when_first_pass_evidence_is_sufficient(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            action="ANALYZE_REGION",
            message="分析区域",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026),
            options={"traceId": "adaptive-run-2", "useKnowledge": False},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual(["gis.queryRegionSummary"], gateway.tool_names)
        self.assertEqual("SKIPPED_SUFFICIENT", response.data["adaptivePlanning"]["status"])
        self.assertEqual([], response.data["adaptivePlanning"]["addedToolNames"])

    async def test_workflow_builds_plan_execution_from_runtime_tool_plan_without_preview(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"traceId": "runtime-plan-run-1", "useKnowledge": True},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)
        plan_execution = response.planExecution

        self.assertTrue(plan_execution["available"])
        self.assertEqual("MATCHED", plan_execution["status"])
        self.assertEqual("ANALYZE_ROUTE", plan_execution["plannedAction"])
        self.assertEqual("ROUTE_ANALYSIS", plan_execution["plannedIntent"])
        self.assertEqual(
            [
                "gis.queryRegionSummary",
                "gis.queryAssessmentResults",
                "gis.queryDiseases",
                "knowledge.retrieve",
            ],
            plan_execution["plannedToolNames"],
        )
        self.assertEqual(plan_execution, response.data["planExecution"])
        self.assertEqual(plan_execution, response.trace["planExecution"])
        self.assertEqual("MATCHED", response.answerMeta["planExecutionStatus"])

    async def test_strategy_metadata_exposes_adaptive_planning(self):
        metadata = strategy_metadata()

        self.assertTrue(metadata["adaptivePlanning"])
        self.assertEqual(1, metadata["maxAdaptiveIterations"])

    async def test_workflow_response_hides_internal_strategy_version(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            action="ANALYZE_OBJECT",
            message="分析当前路线",
            mapContext=MapAiContext(
                mode="OBJECT",
                routeCode="C067140727",
                year=2026,
                mapObject={"objectType": "ROAD_ROUTE", "routeCode": "C067140727"},
            ),
            options={"traceId": "strategy-hidden-run", "useKnowledge": False},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)
        payload = json.dumps(response.model_dump(), ensure_ascii=False)

        self.assertNotIn(settings.strategy_version, payload)
        self.assertNotIn("strategyVersion", payload)
        self.assertNotIn("orchestratorStrategy", payload)

    async def test_workflow_response_exposes_capability_metadata(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"traceId": "capability-run-1", "useKnowledge": True},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual("knowledge.metric_explain", response.answerMeta["capabilityId"])
        self.assertEqual("指标解释", response.answerMeta["capabilityName"])
        self.assertEqual("knowledge.metric_explain", response.trace["capability"]["capabilityId"])
        self.assertEqual("knowledge.metric_explain", response.data["capabilityId"])

    async def test_runtime_governance_blocks_prohibited_planned_tool(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"traceId": "policy-block-run", "useKnowledge": True},
        )

        with patch(
            "app.workflow.plan_tools",
            return_value=[
                ToolCall(toolName="knowledge.retrieve", args={"query": "PCI", "topK": 3}, reason="allowed"),
                ToolCall(toolName="gis.queryRegionSummary", args={"limit": 50}, reason="should be blocked"),
            ],
        ):
            response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual(["knowledge.retrieve"], gateway.tool_names)
        self.assertEqual(["knowledge.retrieve"], [item["toolName"] for item in response.toolResults])
        self.assertEqual("FAIL", response.answerMeta["policyStatus"])
        self.assertEqual("FAIL", response.data["toolPolicy"]["policyStatus"])
        self.assertEqual("FAIL", response.trace["toolPolicy"]["policyStatus"])
        self.assertIn("gis.queryRegionSummary", response.data["toolPolicy"]["blockedToolNames"])
        self.assertIn("gis.queryRegionSummary", response.trace["toolPolicy"]["blockedToolNames"])
        self.assertIn(
            "PROHIBITED_TOOL_BLOCKED",
            [item["code"] for item in response.answerMeta["policyChecks"]],
        )

    async def test_runtime_governance_records_missing_required_tool(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"traceId": "policy-required-run", "useKnowledge": True, "disableAdaptivePlanning": True},
        )

        with patch("app.workflow.plan_tools", return_value=[]):
            response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual([], gateway.tool_names)
        self.assertEqual("FAIL", response.answerMeta["policyStatus"])
        self.assertEqual("FAIL", response.data["toolPolicy"]["policyStatus"])
        self.assertEqual("FAIL", response.trace["toolPolicy"]["policyStatus"])
        self.assertIn(
            "REQUIRED_TOOL_MISSING",
            [item["code"] for item in response.answerMeta["policyChecks"]],
        )


class AdaptiveFakeGateway:
    def __init__(self):
        self.tool_names = []

    async def execute_tool(self, call: ToolCall, **kwargs):
        self.tool_names.append(call.toolName)
        if call.toolName == "gis.queryRegionSummary":
            return ToolResult(toolName=call.toolName, success=True, count=0, data={"items": []})
        if call.toolName == "knowledge.retrieve":
            return ToolResult(toolName=call.toolName, success=True, count=2, data={"sources": [{"title": "规则"}]})
        return ToolResult(toolName=call.toolName, success=False, errorMessage="unexpected tool")


class SufficientFakeGateway:
    def __init__(self):
        self.tool_names = []

    async def execute_tool(self, call: ToolCall, **kwargs):
        self.tool_names.append(call.toolName)
        return ToolResult(toolName=call.toolName, success=True, count=3, data={"items": [{"id": 1}, {"id": 2}, {"id": 3}]})


class FakeLlmClient:
    pass


if __name__ == "__main__":
    unittest.main()
