import copy
import os
import tempfile
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
        self.assertEqual("PASS", body["policyStatus"])
        check_codes = [item["code"] for item in body["policyChecks"]]
        self.assertIn("REQUIRED_TOOLS_PRESENT", check_codes)
        self.assertIn("PROHIBITED_TOOLS_ABSENT", check_codes)

    def test_plan_simulate_uses_draft_registry_when_wrapper_payload_is_sent(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        route_capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "map.route_analysis")
        route_capability["toolPolicy"]["required"] = ["knowledge.retrieve"]
        route_capability["toolPolicy"]["optional"] = []
        route_capability["toolPolicy"]["adaptive"] = []
        route_capability["toolPolicy"]["prohibited"] = ["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases"]

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "request": {
                    "action": "ANALYZE_ROUTE",
                    "message": "分析当前路线",
                    "mapContext": {"mode": "ROUTE", "routeCode": "Y016140727", "year": 2026},
                    "options": {"useKnowledge": True},
                },
                "capabilitiesConfig": capabilities_config,
                "toolsConfig": tools_config,
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("DRAFT_PLAN_SIMULATE", body["mode"])
        self.assertEqual("map.route_analysis", body["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item["toolName"] for item in body["toolPlan"]])
        self.assertIn("gis.queryRegionSummary", body["capability"]["toolPolicy"]["prohibited"])

    def test_plan_simulate_returns_concrete_section_solution_tools(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "action": "GENERATE_OBJECT_SOLUTION",
                "message": "生成当前路段养护计划",
                "mapContext": {
                    "mode": "OBJECT",
                    "routeCode": "Y016140727",
                    "year": 2026,
                    "mapObject": {
                        "objectType": "ROAD_SECTION",
                        "objectId": "section-1",
                        "routeCode": "Y016140727",
                        "startStake": 0,
                        "endStake": 14.072,
                    },
                },
                "actionInput": {"solutionType": "SECTION_PLAN"},
                "options": {"useKnowledge": True, "traceId": "governance-section-solution-test"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("solution.section_plan", body["capabilityId"])
        self.assertEqual(
            ["gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "solution.generateDraft", "knowledge.retrieve"],
            [item["toolName"] for item in body["toolPlan"]],
        )
        self.assertEqual("PASS", body["policyStatus"])

    def test_plan_simulate_uses_draft_solution_tool_policy(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "solution.section_plan")
        capability["toolPolicy"]["required"] = ["knowledge.retrieve"]
        capability["toolPolicy"]["optional"] = []
        capability["toolPolicy"]["adaptive"] = []
        capability["toolPolicy"]["prohibited"] = [
            "gis.queryAssessmentResults",
            "gis.queryDiseases",
            "gis.queryDiseasesByStakeRange",
            "solution.generateDraft",
        ]

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "request": {
                    "action": "GENERATE_OBJECT_SOLUTION",
                    "message": "生成当前路段养护计划",
                    "mapContext": {
                        "mode": "OBJECT",
                        "routeCode": "Y016140727",
                        "year": 2026,
                        "mapObject": {
                            "objectType": "ROAD_SECTION",
                            "objectId": "section-1",
                            "routeCode": "Y016140727",
                            "startStake": 0,
                            "endStake": 14.072,
                        },
                    },
                    "actionInput": {"solutionType": "SECTION_PLAN"},
                    "options": {"useKnowledge": True},
                },
                "capabilitiesConfig": capabilities_config,
                "toolsConfig": tools_config,
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("DRAFT_PLAN_SIMULATE", body["mode"])
        self.assertEqual("solution.section_plan", body["capabilityId"])
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

    def test_draft_policy_coverage_uses_draft_config_without_mutating_active(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "knowledge.metric_explain")
        capability["toolPolicy"]["required"] = ["gis.queryRegionSummary"]
        capability["toolPolicy"]["prohibited"] = []
        capability["examples"][0]["expect"]["exactToolNames"] = ["knowledge.retrieve"]

        response = client.post(
            "/api/srmp/langgraph/governance/policies/coverage/draft",
            json={
                "capabilitiesConfig": capabilities_config,
                "toolsConfig": tools_config,
                "caseIds": ["knowledge.metric_explain.basic"],
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("DRAFT_POLICY_COVERAGE", body["mode"])
        self.assertEqual(1, body["caseCount"])
        self.assertEqual(1, body["failedCount"])
        self.assertEqual("knowledge.metric_explain.basic", body["cases"][0]["id"])
        self.assertIn("gis.queryRegionSummary", body["cases"][0]["actualToolNames"])

        active_coverage = client.get("/api/srmp/langgraph/governance/policies/coverage").json()
        active_case = next(item for item in active_coverage["cases"] if item["id"] == "knowledge.metric_explain.basic")
        self.assertEqual("PASS", active_case["status"])
        self.assertEqual(["knowledge.retrieve"], active_case["actualToolNames"])

    def test_draft_policy_coverage_compares_against_active_cases(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "knowledge.metric_explain")
        capability["toolPolicy"]["required"] = ["gis.queryRegionSummary"]
        capability["toolPolicy"]["optional"] = []
        capability["toolPolicy"]["adaptive"] = []
        capability["toolPolicy"]["prohibited"] = []

        response = client.post(
            "/api/srmp/langgraph/governance/policies/coverage/draft",
            json={
                "capabilitiesConfig": capabilities_config,
                "toolsConfig": tools_config,
                "caseIds": ["knowledge.metric_explain.basic"],
            },
        )

        self.assertEqual(200, response.status_code)
        comparison = response.json()["comparison"]
        self.assertEqual(1, comparison["regressedCount"])
        self.assertEqual(0, comparison["improvedCount"])
        regression = comparison["regressions"][0]
        self.assertEqual("knowledge.metric_explain.basic", regression["id"])
        self.assertEqual("PASS", regression["activeStatus"])
        self.assertEqual("FAIL", regression["draftStatus"])
        self.assertIn("gis.queryRegionSummary", regression["draftToolNames"])

    def test_draft_policy_coverage_filters_selected_cases(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()

        response = client.post(
            "/api/srmp/langgraph/governance/policies/coverage/draft",
            json={
                "capabilitiesConfig": active["capabilitiesConfig"],
                "toolsConfig": active["toolsConfig"],
                "caseIds": ["map.route_analysis.action"],
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual(1, body["caseCount"])
        self.assertEqual(["map.route_analysis.action"], [item["id"] for item in body["cases"]])

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

    def test_readiness_endpoint_aggregates_policy_contract_and_unbound_tools(self):
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
        self.assertEqual(0, body["summary"]["orphanToolCount"])
        issues = {(item["code"], item.get("toolName")): item for item in body["issues"]}
        self.assertIn(("TOOL_MISSING_IN_JAVA", "gis.queryRegionSummary"), issues)
        self.assertEqual("ERROR", issues[("TOOL_MISSING_IN_JAVA", "gis.queryRegionSummary")]["severity"])
        self.assertNotIn(("TOOL_WITHOUT_CAPABILITY", "template.match"), issues)

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

    def test_config_draft_validate_includes_policy_coverage_comparison(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "knowledge.metric_explain")
        capability["toolPolicy"]["required"] = ["gis.queryRegionSummary"]
        capability["toolPolicy"]["optional"] = []
        capability["toolPolicy"]["adaptive"] = []
        capability["toolPolicy"]["prohibited"] = []

        response = client.post(
            "/api/srmp/langgraph/governance/config/draft/validate",
            json={"capabilitiesConfig": capabilities_config, "toolsConfig": tools_config},
        )

        self.assertEqual(200, response.status_code)
        comparison = response.json()["coverageComparison"]
        self.assertGreaterEqual(comparison["caseCount"], 1)
        self.assertGreaterEqual(comparison["regressedCount"], 1)
        self.assertIn("knowledge.metric_explain.basic", [item["id"] for item in comparison["regressions"]])

    def test_publish_request_blocks_failed_draft_policy_coverage(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "knowledge.metric_explain")
        capability["toolPolicy"]["required"] = ["gis.queryRegionSummary"]
        capability["toolPolicy"]["prohibited"] = []
        capability["examples"][0]["expect"]["exactToolNames"] = ["knowledge.retrieve"]

        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "publish.jsonl")
            with patch.dict(os.environ, {"SRMP_AGENT_GOVERNANCE_PUBLISH_LOG_PATH": log_path}):
                response = client.post(
                    "/api/srmp/langgraph/governance/config/publish/request",
                    json={
                        "capabilitiesConfig": capabilities_config,
                        "toolsConfig": tools_config,
                        "reason": "覆盖率失败阻断测试",
                    },
                )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("BLOCKED", body["status"])
        blocker_codes = [item["code"] for item in body["record"]["blockers"]]
        self.assertIn("DRAFT_POLICY_COVERAGE_FAILED", blocker_codes)
        self.assertEqual(1, body["record"]["policyCoverage"]["failedCount"])

    def test_rollback_request_contains_restore_draft_from_base_snapshot(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capabilities_config["capabilities"][0]["name"] = "可回滚发布包"

        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "publish.jsonl")
            with patch.dict(os.environ, {"SRMP_AGENT_GOVERNANCE_PUBLISH_LOG_PATH": log_path}):
                submit = client.post(
                    "/api/srmp/langgraph/governance/config/publish/request",
                    json={
                        "capabilitiesConfig": capabilities_config,
                        "toolsConfig": tools_config,
                        "reason": "回滚草稿测试",
                    },
                ).json()
                request_id = submit["record"]["requestId"]
                client.post(f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}/approve", json={})
                client.post(f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}/apply", json={})

                rollback = client.post(
                    f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}/rollback",
                    json={"reason": "恢复上一版本"},
                ).json()

        self.assertEqual("ROLLBACK_SUBMITTED", rollback["status"])
        rollback_draft = rollback["rollbackDraft"]
        self.assertEqual(request_id, rollback_draft["targetRequestId"])
        self.assertEqual(active["capabilitiesConfig"], rollback_draft["capabilitiesConfig"])
        self.assertEqual(active["toolsConfig"], rollback_draft["toolsConfig"])

    def test_publish_request_detail_keeps_snapshot_out_of_list_and_in_detail(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capabilities_config["capabilities"][0]["name"] = "发布包快照测试"

        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "publish.jsonl")
            with patch.dict(os.environ, {"SRMP_AGENT_GOVERNANCE_PUBLISH_LOG_PATH": log_path}):
                submit_response = client.post(
                    "/api/srmp/langgraph/governance/config/publish/request",
                    json={
                        "capabilitiesConfig": capabilities_config,
                        "toolsConfig": tools_config,
                        "reason": "测试发布详情快照",
                    },
                )
                self.assertEqual(200, submit_response.status_code)
                request_id = submit_response.json()["record"]["requestId"]

                list_response = client.get("/api/srmp/langgraph/governance/config/publish/requests")
                self.assertEqual(200, list_response.status_code)
                list_record = list_response.json()["records"][0]
                self.assertTrue(list_record["configSnapshotAvailable"])
                self.assertNotIn("configSnapshot", list_record)

                detail_response = client.get(f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}")
                self.assertEqual(200, detail_response.status_code)
                detail = detail_response.json()
                self.assertTrue(detail["found"])
                self.assertEqual(request_id, detail["requestId"])
                self.assertEqual("发布包快照测试", detail["configSnapshot"]["capabilitiesConfig"]["capabilities"][0]["name"])
                self.assertEqual(1, len(detail["timeline"]))
                self.assertTrue(detail["package"]["draftHashes"]["capabilities"])


if __name__ == "__main__":
    unittest.main()
