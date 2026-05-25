import unittest

from fastapi.testclient import TestClient

from app.main import app


class GovernanceApiTest(unittest.TestCase):
    def test_capabilities_endpoint_returns_registry(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/governance/capabilities")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertTrue(body["configValid"])
        self.assertTrue(any(item["id"] == "knowledge.metric_explain" for item in body["capabilities"]))

    def test_plan_simulate_returns_capability(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "message": "解释 PCI 指标",
                "mapContext": {"mode": "ROUTE", "routeCode": "Y016140727", "year": 2026},
                "options": {"topK": 3, "traceId": "governance-plan-test"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("knowledge.metric_explain", body["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item["toolName"] for item in body["toolPlan"]])

    def test_policy_coverage_endpoint_runs_configured_examples(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/governance/policies/coverage")

        self.assertEqual(200, response.status_code)
        body = response.json()
        cases = {item["id"]: item for item in body["cases"]}

        self.assertGreaterEqual(body["caseCount"], 8)
        self.assertEqual(0, body["failedCount"])
        self.assertIn("knowledge.metric_explain.basic", cases)
        self.assertEqual("PASS", cases["knowledge.metric_explain.basic"]["status"])
        self.assertEqual("knowledge.metric_explain", cases["knowledge.metric_explain.basic"]["actualCapabilityId"])
        self.assertEqual(["knowledge.retrieve"], cases["knowledge.metric_explain.basic"]["actualToolNames"])
        self.assertIn("map.route_analysis.context_metric", cases)
        self.assertEqual("map.route_analysis", cases["map.route_analysis.context_metric"]["actualCapabilityId"])
        self.assertIn("gis.queryRegionSummary", cases["map.route_analysis.context_metric"]["actualToolNames"])

    def test_tool_impact_endpoint_returns_capability_relations(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/governance/tools/impact")

        self.assertEqual(200, response.status_code)
        body = response.json()
        tools = {item["name"]: item for item in body["tools"]}

        self.assertIn("knowledge.retrieve", tools)
        knowledge = tools["knowledge.retrieve"]
        self.assertGreaterEqual(knowledge["affectedCapabilityCount"], 2)
        self.assertIn("knowledge.metric_explain", [item["id"] for item in knowledge["requiredBy"]])
        self.assertIn("map.route_analysis", [item["id"] for item in knowledge["optionalBy"]])
        self.assertIn("map.region_analysis", [item["id"] for item in knowledge["adaptiveBy"]])

        region_summary = tools["gis.queryRegionSummary"]
        self.assertIn("knowledge.metric_explain", [item["id"] for item in region_summary["prohibitedBy"]])
        self.assertIn("map.route_analysis", [item["id"] for item in region_summary["requiredBy"]])


if __name__ == "__main__":
    unittest.main()
