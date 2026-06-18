import unittest
from unittest.mock import patch

from app.map_agent_run import MapAgentRunWorkflow
from app.schemas import MapAgentRunRequest, MapAiAgentResponse, MapAiContext, ToolResult


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
        self.assertEqual("solution.section_plan", response.answerMeta["capabilityId"])
        self.assertEqual("生成路段养护计划", response.answerMeta["capabilityName"])
        self.assertEqual("solution.section_plan", response.trace["capability"]["capabilityId"])
        self.assertEqual("solution.section_plan", response.data["capabilityId"])
        self.assertEqual("PASS", response.answerMeta["policyStatus"])
        self.assertEqual("PASS", response.data["toolPolicy"]["policyStatus"])
        self.assertEqual("PASS", response.trace["toolPolicy"]["policyStatus"])
        business_summary = next(item for item in response.sources if item.get("sourceTitle") == "业务证据")
        self.assertEqual("NONE", business_summary.get("bindingType"))
        self.assertEqual("NONE", business_summary.get("bindingOrigin"))
        self.assertEqual("VALID", business_summary.get("bindingStatus"))
        self.assertNotIn("mapTarget", business_summary)
        self.assertNotIn("mapTarget", business_summary["followupContext"])
        business_source = next(
            item
            for item in response.sources
            if item.get("toolName") == "gis.queryDiseases"
            and item.get("objectId") == "a"
        )
        self.assertEqual("OBJECT", business_source.get("bindingType"))
        self.assertEqual("BUSINESS_QUERY", business_source.get("bindingOrigin"))
        self.assertEqual("UNVERIFIED", business_source.get("bindingStatus"))
        self.assertEqual("DISEASE", business_source["mapTarget"]["objectType"])
        self.assertEqual("a", business_source["mapTarget"]["objectId"])

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

    async def test_analysis_run_creates_evidence_snapshot(self):
        store = FakeSnapshotStore()
        workflow = MapAgentRunWorkflow(base_workflow=FakeAnalysisWorkflow(), gateway=RecordingGateway(), evidence_snapshot_store=store)

        response = await workflow.run(
            MapAgentRunRequest(
                action="ANALYZE_ROUTE",
                message="分析当前路线",
                mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727"),
                options={"traceId": "analysis-trace-1"},
            ),
            tenant_id="default",
            trace_id=None,
        )

        self.assertEqual(1, len(store.created))
        self.assertEqual("evs-test", response.answerMeta["evidenceSnapshotId"])
        self.assertEqual("sha256:test", response.answerMeta["scopeFingerprint"])
        self.assertEqual("evs-test", response.data["evidenceSnapshot"]["evidenceSnapshotId"])
        self.assertEqual("analysis-trace-1", response.data["evidenceSnapshot"]["analysisTraceId"])
        self.assertEqual("evs-test", response.trace["evidenceSnapshot"]["evidenceSnapshotId"])

    async def test_solution_generation_reuses_matching_analysis_snapshot(self):
        snapshot = {
            "evidenceSnapshotId": "evs-test",
            "analysisTraceId": "analysis-trace-1",
            "scopeFingerprint": "sha256:test",
            "toolResults": [
                {"toolName": "gis.queryAssessmentResults", "success": True, "data": {"items": [{"routeCode": "Y016140727"}]}, "count": 1},
                {"toolName": "gis.queryDiseasesByStakeRange", "success": True, "data": {"items": [{"routeCode": "Y016140727"}]}, "count": 1},
            ],
            "sources": [{"sourceType": "BUSINESS_DATA", "mapTarget": {"routeCode": "Y016140727", "objectId": "section-1"}}],
            "evidence": {"toolSuccessCount": 2, "businessHitCount": 2},
        }
        gateway = RecordingGateway()
        workflow = MapAgentRunWorkflow(base_workflow=None, gateway=gateway, evidence_snapshot_store=FakeSnapshotStore(snapshot))

        response = await workflow.run(
            MapAgentRunRequest(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前路段养护计划",
                mapContext=MapAiContext(
                    mode="OBJECT",
                    routeCode="Y016140727",
                    mapObject={"objectType": "ROAD_SECTION", "objectId": "section-1", "routeCode": "Y016140727"},
                ),
                actionInput={"solutionType": "SECTION_PLAN", "evidenceSnapshotId": "evs-test"},
                options={"traceId": "generate-trace-1", "useKnowledge": True},
            ),
            tenant_id="default",
            trace_id="generate-trace-1",
        )

        executed_names = [call.toolName for call in gateway.calls]
        self.assertEqual(["solution.generateDraft"], executed_names)
        self.assertEqual("REUSED", response.answerMeta["evidenceReuseStatus"])
        self.assertEqual("analysis-trace-1", response.answerMeta["basedOnAnalysisTraceId"])
        self.assertEqual("evs-test", response.answerMeta["evidenceSnapshotId"])


class FakeSnapshotStore:
    def __init__(self, snapshot=None):
        self.snapshot = snapshot
        self.created = []

    def create(self, **kwargs):
        record = {
            "evidenceSnapshotId": "evs-test",
            "analysisTraceId": kwargs.get("analysis_trace_id"),
            "scopeFingerprint": "sha256:test",
            "toolResults": kwargs.get("tool_results") or [],
            "sources": kwargs.get("sources") or [],
            "evidence": kwargs.get("evidence") or {},
        }
        self.created.append(record)
        return record

    def get(self, snapshot_id, map_context, now_ms=None):
        return self.snapshot if snapshot_id == "evs-test" else None


class FakeAnalysisWorkflow:
    async def run(self, request, tenant_id, trace_id):
        return MapAiAgentResponse(
            answer="路线分析完成",
            intent="ROUTE_ANALYSIS",
            mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
            toolResults=[{"toolName": "gis.queryDiseases", "success": True, "count": 2}],
            sources=[{"sourceType": "BUSINESS_DATA", "sourceTitle": "病害记录", "mapTarget": {"routeCode": "Y016140727"}}],
            data={
                "answerMeta": {"capabilityId": "map.route_analysis", "answerSource": "LLM"},
                "evidence": {"businessHitCount": 2},
            },
        )


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
