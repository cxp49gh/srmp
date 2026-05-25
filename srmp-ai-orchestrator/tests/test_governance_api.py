import copy
import unittest
from unittest.mock import AsyncMock, patch

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

    def test_capability_detail_endpoint_returns_tools_examples_and_developer_hints(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/governance/capabilities/map.route_analysis")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("map.route_analysis", body["capability"]["id"])
        self.assertEqual("路线分析", body["capability"]["name"])
        self.assertIn("map.route_analysis.action", [item["id"] for item in body["examples"]])
        self.assertIn("gis.queryRegionSummary", [item["name"] for item in body["tools"]["required"]])
        self.assertIn("knowledge.retrieve", [item["name"] for item in body["tools"]["optional"]])
        self.assertIn("srmp-ai-orchestrator/app/governance_data/capabilities.json", body["developerGuide"]["configFiles"])
        self.assertIn("配置能力触发条件", body["developerGuide"]["steps"])

    def test_tool_detail_endpoint_returns_contract_and_capability_relations(self):
        client = TestClient(app)
        contract = {
            "ok": True,
            "javaTools": ["knowledge.retrieve", "gis.queryRegionSummary"],
            "runtimeAllowedTools": ["knowledge.retrieve"],
            "missingInJava": [],
            "blockedByRuntimeWhitelist": ["gis.queryRegionSummary"],
            "writeBlocked": [],
        }

        with patch("app.main.gateway.inspect_contract", new=AsyncMock(return_value=contract)):
            response = client.get("/api/srmp/langgraph/governance/tools/knowledge.retrieve?includeContract=true")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("knowledge.retrieve", body["tool"]["name"])
        self.assertEqual("检索知识库", body["tool"]["label"])
        self.assertTrue(body["contract"]["javaRegistered"])
        self.assertTrue(body["contract"]["runtimeAllowed"])
        self.assertFalse(body["contract"]["writeBlocked"])
        self.assertIn("knowledge.metric_explain", [item["id"] for item in body["requiredBy"]])
        self.assertIn("map.route_analysis", [item["id"] for item in body["optionalBy"]])
        self.assertIn("srmp-ai-orchestrator/app/governance_data/tools.json", body["developerGuide"]["configFiles"])
        self.assertIn("在 Java AiToolRegistry 注册实现并暴露到 Tool Gateway", body["developerGuide"]["steps"])

    def test_readiness_endpoint_aggregates_policy_contract_and_orphan_tools(self):
        client = TestClient(app)
        contract = {
            "ok": True,
            "javaTools": ["knowledge.retrieve", "gis.queryAssessmentResults", "gis.queryDiseases"],
            "runtimeAllowedTools": ["knowledge.retrieve", "gis.queryAssessmentResults", "gis.queryDiseases"],
            "missingInJava": ["gis.queryRegionSummary"],
            "blockedByRuntimeWhitelist": [],
            "writeBlocked": [],
        }

        with patch("app.main.gateway.inspect_contract", new=AsyncMock(return_value=contract)):
            response = client.get("/api/srmp/langgraph/governance/readiness?includeContract=true&runCoverage=true")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("FAIL", body["status"])
        self.assertEqual(0, body["summary"]["policyFailedCount"])
        self.assertGreaterEqual(body["summary"]["orphanToolCount"], 1)
        issues = {(item["code"], item.get("toolName")): item for item in body["issues"]}
        self.assertIn(("TOOL_MISSING_IN_JAVA", "gis.queryRegionSummary"), issues)
        self.assertEqual("ERROR", issues[("TOOL_MISSING_IN_JAVA", "gis.queryRegionSummary")]["severity"])
        self.assertIn(("TOOL_WITHOUT_CAPABILITY", "template.match"), issues)
        self.assertEqual("WARN", issues[("TOOL_WITHOUT_CAPABILITY", "template.match")]["severity"])

    def test_config_endpoint_returns_active_governance_bundle(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/governance/config")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("ACTIVE", body["mode"])
        self.assertFalse(body["runtimeMutable"])
        self.assertEqual("restart-required", body["publishMode"])
        self.assertEqual("phase52-governance-v1", body["capabilitiesConfig"]["version"])
        self.assertEqual("phase52-governance-v1", body["toolsConfig"]["version"])
        self.assertTrue(body["capabilitiesHash"])
        self.assertTrue(body["toolsHash"])
        self.assertIn("srmp-ai-orchestrator/app/governance_data/capabilities.json", body["configFiles"]["capabilities"])
        self.assertIn("srmp-ai-orchestrator/app/governance_data/tools.json", body["configFiles"]["tools"])
        self.assertTrue(body["configValid"])

    def test_config_draft_validate_detects_invalid_tool_reference(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = active["capabilitiesConfig"]
        tools_config = active["toolsConfig"]
        first_capability = capabilities_config["capabilities"][0]
        first_capability.setdefault("toolPolicy", {}).setdefault("required", []).append("missing.tool")

        response = client.post(
            "/api/srmp/langgraph/governance/config/draft/validate",
            json={"capabilitiesConfig": capabilities_config, "toolsConfig": tools_config},
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("DRAFT_VALIDATE", body["mode"])
        self.assertTrue(body["draftId"])
        self.assertFalse(body["configValid"])
        self.assertEqual("FAIL", body["readiness"]["status"])
        error_codes = [item["code"] for item in body["validation"]["errors"]]
        self.assertIn("TOOL_NOT_FOUND", error_codes)
        self.assertGreaterEqual(body["readiness"]["summary"]["validationErrorCount"], 1)

    def test_config_draft_validate_returns_diff_against_active_config(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capabilities_config["capabilities"][0]["name"] = "指标解释草稿"
        tools_config["tools"][0]["description"] = "草稿中的知识检索工具说明"

        response = client.post(
            "/api/srmp/langgraph/governance/config/draft/validate",
            json={"capabilitiesConfig": capabilities_config, "toolsConfig": tools_config},
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        diff = body["diff"]
        self.assertTrue(diff["changed"])
        self.assertEqual(active["capabilitiesHash"], diff["baseHashes"]["capabilities"])
        self.assertEqual(body["capabilitiesHash"], diff["draftHashes"]["capabilities"])
        self.assertGreaterEqual(diff["summary"]["capabilityModifiedCount"], 1)
        self.assertGreaterEqual(diff["summary"]["toolModifiedCount"], 1)
        capability_changes = {item["id"]: item for item in diff["capabilities"]}
        tool_changes = {item["name"]: item for item in diff["tools"]}
        self.assertEqual("MODIFIED", capability_changes["knowledge.metric_explain"]["changeType"])
        self.assertIn("name", capability_changes["knowledge.metric_explain"]["changedFields"])
        self.assertEqual("MODIFIED", tool_changes["knowledge.retrieve"]["changeType"])
        self.assertIn("description", tool_changes["knowledge.retrieve"]["changedFields"])


if __name__ == "__main__":
    unittest.main()
