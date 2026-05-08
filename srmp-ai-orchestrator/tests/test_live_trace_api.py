import unittest

from fastapi.testclient import TestClient

from app.main import app, live_trace_store


class LiveTraceApiTest(unittest.TestCase):
    def test_live_trace_endpoint_returns_snapshot_and_not_found_payload(self):
        live_trace_store.start_trace("api-trace-1", action="CHAT", graph_name="chat_graph")
        live_trace_store.start_step("api-trace-1", "answer_generate", "生成回答")

        client = TestClient(app)
        found = client.get("/api/srmp/langgraph/trace/live/api-trace-1")
        self.assertEqual(200, found.status_code)
        self.assertEqual("RUNNING", found.json()["status"])
        self.assertEqual("answer_generate", found.json()["currentStep"]["name"])

        missing = client.get("/api/srmp/langgraph/trace/live/missing-trace")
        self.assertEqual(200, missing.status_code)
        self.assertEqual("NOT_FOUND", missing.json()["status"])
