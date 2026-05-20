import unittest

from app.live_trace import LiveTraceStore


class LiveTraceStoreTest(unittest.TestCase):
    def test_records_running_success_and_current_step(self):
        store = LiveTraceStore(max_records=3)
        store.start_trace("trace-1", action="ANALYZE_REGION", graph_name="region_analysis_graph")
        store.start_step("trace-1", "tool_execute", "执行只读工具", data={"planned": 2})
        running = store.get("trace-1")

        self.assertEqual("RUNNING", running["status"])
        self.assertEqual("tool_execute", running["currentStep"]["name"])
        self.assertEqual("RUNNING", running["currentStep"]["status"])

        store.complete_step("trace-1", {
            "name": "tool_execute",
            "label": "执行只读工具",
            "status": "SUCCESS",
            "count": 2,
            "elapsedMs": 120,
            "data": {
                "success": 2,
                "failed": 0,
                "tools": ["knowledge.retrieve", "gis.region.summary"]
            }
        })
        store.complete_trace("trace-1", answer_meta={"llmStatus": "SUCCESS"})
        completed = store.get("trace-1")

        self.assertEqual("SUCCESS", completed["status"])
        self.assertIsNone(completed["currentStep"])
        self.assertEqual(1, len(completed["steps"]))
        self.assertEqual(2, completed["toolSummary"]["completed"])
        self.assertEqual("SUCCESS", completed["answerMeta"]["llmStatus"])

    def test_records_failure_and_not_found(self):
        store = LiveTraceStore(max_records=3)
        store.start_trace("trace-2", action="CHAT", graph_name="chat_graph")
        store.start_step("trace-2", "answer_generate", "生成回答")
        store.fail_step("trace-2", "answer_generate", "LLM timeout")
        failed = store.get("trace-2")

        self.assertEqual("FAILED", failed["status"])
        self.assertEqual("LLM timeout", failed["error"])
        self.assertEqual("FAILED", failed["steps"][0]["status"])
        self.assertEqual("NOT_FOUND", store.not_found("missing")["status"])

    def test_records_business_scope(self):
        store = LiveTraceStore(max_records=3)
        store.start_trace(
            "trace-scope",
            action="ANALYZE_OBJECT",
            graph_name="object_analysis_graph",
            business_scope={"projectId": "project-2026", "routeCode": "Y016140727", "sectionTier": "LEDGER"},
        )

        running = store.get("trace-scope")

        self.assertEqual("project-2026", running["businessScope"]["projectId"])
        self.assertEqual("LEDGER", running["businessScope"]["sectionTier"])

    def test_prunes_old_records(self):
        store = LiveTraceStore(max_records=2)
        store.start_trace("trace-a", action="CHAT", graph_name="chat_graph")
        store.start_trace("trace-b", action="CHAT", graph_name="chat_graph")
        store.start_trace("trace-c", action="CHAT", graph_name="chat_graph")

        self.assertIsNone(store.get("trace-a"))
        self.assertIsNotNone(store.get("trace-b"))
        self.assertIsNotNone(store.get("trace-c"))


if __name__ == "__main__":
    unittest.main()
