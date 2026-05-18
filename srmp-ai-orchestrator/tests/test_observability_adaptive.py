import unittest

from app.observability import RuntimeAuditStore
from app.schemas import ActionResult, MapAiAgentRequest, MapAiAgentResponse


class AdaptiveObservabilityTest(unittest.TestCase):
    def test_record_success_classifies_failed_action_result_as_failed(self):
        store = RuntimeAuditStore(max_records=10, persist_enabled=False)
        response = MapAiAgentResponse(
            answer="方案生成失败",
            action="GENERATE_OBJECT_SOLUTION",
            actionResult=ActionResult(
                type="SOLUTION_PREVIEW",
                status="FAILED",
                errorMessage="业务工具返回失败",
            ),
            toolResults=[
                {"toolName": "solution.generateDraft", "success": False, "errorMessage": "业务工具返回失败"},
            ],
            data={},
        )

        record = store.record_success(
            request=MapAiAgentRequest(message="生成当前对象的结构化养护建议"),
            response=response,
            tenant_id="default",
            trace_id="failed-action-trace",
            cost_ms=88,
        )

        summary = store.summary()
        self.assertEqual("FAILED", record["status"])
        self.assertEqual("FAILED", record["actionResultStatus"])
        self.assertEqual(1, record["toolTotalCount"])
        self.assertEqual(0, record["toolSuccessCount"])
        self.assertEqual(1, record["toolFailedCount"])
        self.assertTrue(record["fallbackLike"])
        self.assertEqual(1, summary["failed"])
        self.assertEqual(0, summary["success"])

    def test_record_success_flattens_adaptive_planning(self):
        store = RuntimeAuditStore(max_records=10, persist_enabled=False)
        response = MapAiAgentResponse(
            answer="ok",
            data={
                "adaptivePlanning": {
                    "enabled": True,
                    "status": "EXECUTED",
                    "reason": "业务工具未命中，追加知识检索补充解释依据。",
                    "addedToolNames": ["knowledge.retrieve"],
                },
                "toolTotalCount": 2,
                "toolSuccessCount": 2,
                "toolFailedCount": 0,
            },
            trace={"traceId": "adaptive-trace-1"},
            toolResults=[
                {"toolName": "gis.queryRegionSummary", "success": True},
                {"toolName": "knowledge.retrieve", "success": True},
            ],
        )

        record = store.record_success(
            request=MapAiAgentRequest(message="分析区域"),
            response=response,
            tenant_id="default",
            trace_id="adaptive-trace-1",
            cost_ms=42,
        )

        self.assertEqual("EXECUTED", record["adaptivePlanningStatus"])
        self.assertTrue(record["adaptivePlanningEnabled"])
        self.assertEqual(1, record["adaptiveAddedToolCount"])
        self.assertEqual(["knowledge.retrieve"], record["adaptiveAddedToolNames"])
        self.assertEqual("业务工具未命中，追加知识检索补充解释依据。", record["adaptivePlanningReason"])

    def test_summary_aggregates_adaptive_planning(self):
        store = RuntimeAuditStore(max_records=10, persist_enabled=False)
        store.record_success(
            request=MapAiAgentRequest(message="a"),
            response=MapAiAgentResponse(
                answer="a",
                data={"adaptivePlanning": {"enabled": True, "status": "EXECUTED", "addedToolNames": ["knowledge.retrieve"]}},
            ),
            tenant_id="default",
            trace_id="trace-a",
            cost_ms=10,
        )
        store.record_success(
            request=MapAiAgentRequest(message="b"),
            response=MapAiAgentResponse(
                answer="b",
                data={"adaptivePlanning": {"enabled": True, "status": "SKIPPED_SUFFICIENT", "addedToolNames": []}},
            ),
            tenant_id="default",
            trace_id="trace-b",
            cost_ms=10,
        )
        store.record_success(
            request=MapAiAgentRequest(message="c"),
            response=MapAiAgentResponse(answer="c", data={}),
            tenant_id="default",
            trace_id="trace-c",
            cost_ms=10,
        )

        adaptive = store.summary()["adaptivePlanning"]

        self.assertEqual(1, adaptive["executedCount"])
        self.assertEqual(1, adaptive["skippedCount"])
        self.assertEqual(1, adaptive["addedToolCount"])
        self.assertEqual({"EXECUTED": 1, "SKIPPED_SUFFICIENT": 1}, adaptive["statusBuckets"])
        self.assertEqual({"knowledge.retrieve": 1}, adaptive["addedToolBuckets"])


if __name__ == "__main__":
    unittest.main()
