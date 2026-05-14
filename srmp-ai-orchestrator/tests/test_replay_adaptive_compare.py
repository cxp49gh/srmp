import unittest

from fastapi.testclient import TestClient

import app.main as main_module
from app.observability import RuntimeAuditStore
from app.schemas import MapAiAgentRequest, MapAiAgentResponse


class ReplayAdaptiveCompareTest(unittest.TestCase):
    def setUp(self):
        self.original_store = main_module.runtime_audit_store
        self.original_workflow = main_module.workflow
        self.store = RuntimeAuditStore(max_records=10, persist_enabled=False)
        main_module.runtime_audit_store = self.store
        self.workflow = FakeReplayWorkflow()
        main_module.workflow = self.workflow

    def tearDown(self):
        main_module.runtime_audit_store = self.original_store
        main_module.workflow = self.original_workflow

    def test_compare_executes_baseline_off_and_adaptive_default(self):
        source = self.store.record_success(
            request=MapAiAgentRequest(
                message="分析区域",
                mapContext={"tenantId": "default", "mode": "REGION", "routeCode": "G210", "year": 2026},
                options={"traceId": "source-trace", "topK": 3},
            ),
            response=MapAiAgentResponse(answer="ok", intent="REGION_ANALYSIS", data={}),
            tenant_id="default",
            trace_id="source-trace",
            cost_ms=10,
        )

        client = TestClient(main_module.app)
        response = client.post(f"/api/srmp/langgraph/debug/replay/{source['id']}?execute=true&adaptiveMode=compare")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertTrue(body["execute"])
        self.assertEqual("compare", body["adaptiveMode"])
        self.assertEqual("DISABLED", body["compare"]["baselineAdaptiveStatus"])
        self.assertEqual("EXECUTED", body["compare"]["adaptiveStatus"])
        self.assertEqual(1, body["compare"]["toolDelta"])
        self.assertTrue(body["compare"]["evidenceImproved"])
        self.assertEqual(0, body["compare"]["baselineKnowledgeHitCount"])
        self.assertEqual(2, body["compare"]["adaptiveKnowledgeHitCount"])
        self.assertTrue(self.workflow.calls[0]["options"]["disableAdaptivePlanning"])
        self.assertNotIn("disableAdaptivePlanning", self.workflow.calls[1]["options"])

    def test_compare_requires_execute_replay(self):
        source = self.store.record_success(
            request=MapAiAgentRequest(message="分析区域", options={"traceId": "source-trace"}),
            response=MapAiAgentResponse(answer="ok", data={}),
            tenant_id="default",
            trace_id="source-trace",
            cost_ms=10,
        )

        client = TestClient(main_module.app)
        response = client.post(f"/api/srmp/langgraph/debug/replay/{source['id']}?execute=false&adaptiveMode=compare")

        self.assertEqual(400, response.status_code)
        self.assertIn("compare", response.json()["detail"])


class FakeReplayWorkflow:
    def __init__(self):
        self.calls = []

    async def run(self, request, tenant_id, trace_id):
        options = dict(request.options or {})
        self.calls.append({"tenant_id": tenant_id, "trace_id": trace_id, "options": options})
        disabled = bool(options.get("disableAdaptivePlanning"))
        evidence = {
            "sufficient": not disabled,
            "businessHitCount": 0,
            "knowledgeHitCount": 0 if disabled else 2,
            "toolSuccessCount": 1 if disabled else 2,
            "toolFailedCount": 0,
        }
        adaptive = {
            "enabled": not disabled,
            "status": "DISABLED" if disabled else "EXECUTED",
            "addedToolNames": [] if disabled else ["knowledge.retrieve"],
            "evidenceBefore": {
                "sufficient": False,
                "businessHitCount": 0,
                "knowledgeHitCount": 0,
                "toolSuccessCount": 1,
                "toolFailedCount": 0,
            },
            "evidenceAfter": evidence,
        }
        return MapAiAgentResponse(
            answer="baseline" if disabled else "adaptive",
            intent="REGION_ANALYSIS",
            trace={"traceId": trace_id, "adaptivePlanning": adaptive},
            data={
                "adaptivePlanning": adaptive,
                "toolTotalCount": 1 if disabled else 2,
                "toolSuccessCount": 1 if disabled else 2,
                "toolFailedCount": 0,
                "evidence": evidence,
            },
        )

    async def plan(self, request, tenant_id, trace_id):
        return {"traceId": trace_id, "toolPlan": []}


if __name__ == "__main__":
    unittest.main()
