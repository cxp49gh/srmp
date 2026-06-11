import unittest
from unittest.mock import patch

from app.map_agent_run import MapAgentRunWorkflow
from app.schemas import MapAgentRunRequest, MapAiContext, ToolResult


class MapAgentSolutionEvidenceTest(unittest.IsolatedAsyncioTestCase):
    async def test_solution_generation_collects_business_evidence_before_draft(self):
        gateway = RecordingGateway()
        workflow = MapAgentRunWorkflow(base_workflow=None, gateway=gateway)
        request = MapAgentRunRequest(
            action="GENERATE_OBJECT_SOLUTION",
            message="生成当前对象的结构化养护建议",
            mapContext=MapAiContext(
                tenantId="default",
                mode="OBJECT",
                routeCode="Y016140727",
                year=2026,
                mapObject={
                    "objectType": "ROAD_SECTION",
                    "objectId": "section-1",
                    "routeCode": "Y016140727",
                    "startStake": 0,
                    "endStake": 14.072,
                },
                selectedLayers=["ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
                extra={"rawContext": {"query": {"projectId": "project-2026", "sectionTier": "LINE"}}},
            ),
            actionInput={
                "objectType": "ROAD_SECTION",
                "objectId": "section-1",
                "routeCode": "Y016140727",
                "year": 2026,
                "solutionType": "SECTION_PLAN",
            },
            options={"useBusinessData": True, "useKnowledge": True, "topK": 5, "requireAi": False, "traceId": "solution-evidence-test"},
        )

        response = await workflow.run(request, tenant_id="default", trace_id="solution-evidence-test")
        tool_names = [item["toolName"] for item in response.toolResults]
        draft_call = gateway.calls[-1]

        self.assertEqual("solution.generateDraft", draft_call.toolName)
        self.assertIn("gis.queryAssessmentResults", tool_names)
        self.assertIn("gis.queryDiseases", tool_names)
        self.assertIn("gis.queryDiseasesByStakeRange", tool_names)
        self.assertIn("knowledge.retrieve", tool_names)
        self.assertIn("businessEvidence", draft_call.args)
        self.assertEqual(4, draft_call.args["businessEvidence"]["toolSuccessCount"])
        self.assertEqual("SOLUTION_PREVIEW", response.actionResult.type)
        self.assertEqual("solution.generate", response.answerMeta["capabilityId"])
        self.assertEqual("方案生成", response.answerMeta["capabilityName"])
        self.assertEqual("solution.generate", response.trace["capability"]["capabilityId"])
        self.assertEqual("solution.generate", response.data["capabilityId"])
        self.assertEqual("PASS", response.answerMeta["policyStatus"])
        self.assertEqual("PASS", response.data["toolPolicy"]["policyStatus"])
        self.assertEqual("PASS", response.trace["toolPolicy"]["policyStatus"])
        business_summary = next(item for item in response.sources if item.get("sourceTitle") == "业务证据")
        self.assertEqual("ROAD_SECTION", business_summary["mapTarget"]["objectType"])
        self.assertEqual("section-1", business_summary["mapTarget"]["objectId"])
        self.assertEqual("Y016140727", business_summary["mapTarget"]["routeCode"])

    async def test_solution_generation_blocks_prohibited_evidence_tool(self):
        gateway = RecordingGateway()
        workflow = MapAgentRunWorkflow(base_workflow=None, gateway=gateway)
        request = MapAgentRunRequest(
            action="GENERATE_OBJECT_SOLUTION",
            message="生成当前对象的结构化养护建议",
            mapContext=MapAiContext(
                tenantId="default",
                mode="OBJECT",
                routeCode="Y016140727",
                year=2026,
                mapObject={
                    "objectType": "ROAD_SECTION",
                    "objectId": "section-1",
                    "routeCode": "Y016140727",
                    "startStake": 0,
                    "endStake": 14.072,
                },
                selectedLayers=["ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
            ),
            actionInput={"objectType": "ROAD_SECTION", "objectId": "section-1", "routeCode": "Y016140727", "year": 2026},
            options={"useBusinessData": True, "useKnowledge": True, "topK": 5, "traceId": "solution-policy-test"},
        )
        capability = {
            "capabilityId": "solution.generate",
            "name": "方案生成",
            "intent": "SOLUTION_GENERATE",
            "contextUsage": "BUSINESS_SCOPE_REQUIRED",
            "toolPolicy": {
                "required": ["solution.generateDraft"],
                "optional": ["knowledge.retrieve"],
                "adaptive": ["knowledge.retrieve"],
                "prohibited": ["gis.queryDiseases"],
            },
        }

        with patch("app.map_agent_run.resolve_capability", return_value=capability):
            response = await workflow.run(request, tenant_id="default", trace_id="solution-policy-test")

        called_tool_names = [item.toolName for item in gateway.calls]
        response_tool_names = [item["toolName"] for item in response.toolResults]
        self.assertNotIn("gis.queryDiseases", called_tool_names)
        self.assertNotIn("gis.queryDiseases", response_tool_names)
        self.assertIn("solution.generateDraft", called_tool_names)
        self.assertEqual("FAIL", response.answerMeta["policyStatus"])
        self.assertIn("gis.queryDiseases", response.data["toolPolicy"]["blockedToolNames"])
        self.assertIn("PROHIBITED_TOOL_BLOCKED", [item["code"] for item in response.answerMeta["policyChecks"]])


class RecordingGateway:
    def __init__(self):
        self.calls = []

    async def execute_tool(self, call, request, tenant_id, trace_id):
        self.calls.append(call)
        if call.toolName == "solution.generateDraft":
            return ToolResult(
                toolName=call.toolName,
                success=True,
                summary="对象方案预览已生成",
                data={
                    "title": "路段养护计划草稿",
                    "markdown": "## 路段养护计划\n已结合业务证据生成。",
                    "answerMeta": {"answerSource": "TEMPLATE", "llmSuccess": False, "llmStatus": "SKIPPED"},
                    "sourceSummaries": [{"sourceType": "BUSINESS_DATA", "sourceTitle": "业务证据"}],
                },
                count=1,
                costMs=5,
            )
        if call.toolName == "knowledge.retrieve":
            return ToolResult(
                toolName=call.toolName,
                success=True,
                summary="知识库命中 1 条",
                data={"hits": [{"title": "养护规范", "content": "病害处置依据"}]},
                count=1,
                costMs=3,
            )
        return ToolResult(
            toolName=call.toolName,
            success=True,
            summary=f"{call.toolName} 命中 2 条",
            data={"items": [{"id": "a"}, {"id": "b"}], "totalCount": 2, "queryScope": call.args},
            count=2,
            costMs=2,
        )


if __name__ == "__main__":
    unittest.main()
