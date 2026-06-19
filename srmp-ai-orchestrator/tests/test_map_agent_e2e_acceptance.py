import unittest

import app.map_agent_e2e_acceptance as acceptance
from app.map_agent_e2e_acceptance import (
    AcceptanceCase,
    CaseRunResult,
    build_acceptance_cases,
    validate_case_response,
)


class MapAgentE2EAcceptanceTest(unittest.TestCase):
    def test_builds_core_analysis_and_generation_cases(self):
        samples = {
            "projectId": "project-1",
            "route": {
                "objectType": "ROAD_ROUTE",
                "objectId": "route-1",
                "id": "route-1",
                "routeCode": "Y016140727",
                "routeName": "测试路线",
                "startStake": 0,
                "endStake": 10,
            },
            "section": {
                "objectType": "ROAD_SECTION",
                "objectId": "section-1",
                "id": "section-1",
                "routeCode": "Y016140727",
                "startStake": 1,
                "endStake": 2,
            },
            "disease": {
                "objectType": "DISEASE",
                "objectId": "disease-1",
                "id": "disease-1",
                "routeCode": "Y016140727",
                "startStake": 1.2,
                "endStake": 1.2,
                "diseaseName": "裂缝",
            },
            "assessment": {
                "objectType": "ASSESSMENT_RESULT",
                "objectId": "assessment-1",
                "id": "assessment-1",
                "routeCode": "Y016140727",
                "year": 2026,
                "startStake": 1,
                "endStake": 1.1,
                "mqi": 82.4,
            },
            "geometry": {
                "type": "Polygon",
                "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.2], [112.1, 37.1]]],
            },
        }

        cases = build_acceptance_cases(samples, require_ai=True)
        case_ids = {case.case_id for case in cases}

        self.assertIn("knowledge.metric_explain", case_ids)
        self.assertIn("map.route_analysis", case_ids)
        self.assertIn("map.section_analysis", case_ids)
        self.assertIn("map.disease_analysis", case_ids)
        self.assertIn("map.assessment_analysis", case_ids)
        self.assertIn("map.region_analysis", case_ids)
        self.assertIn("solution.route_report", case_ids)
        self.assertIn("solution.section_plan", case_ids)
        self.assertIn("solution.disease_review", case_ids)
        self.assertIn("solution.assessment_advice", case_ids)
        self.assertIn("solution.region_advice", case_ids)

        section_case = next(case for case in cases if case.case_id == "map.section_analysis")
        self.assertEqual("ANALYZE_OBJECT", section_case.payload["action"])
        self.assertEqual("Y016140727", section_case.payload["mapContext"]["mapObject"]["routeCode"])

    def test_metric_question_rejects_business_tools(self):
        case = AcceptanceCase(
            case_id="knowledge.metric_explain",
            name="解释 PCI 指标",
            payload={},
            expected_capability="knowledge.metric_explain",
            required_tools=["knowledge.retrieve"],
            prohibited_tools=["gis.queryRegionSummary", "gis.queryDiseases"],
        )
        response = {
            "answer": "PCI 是路面损坏状况指数。",
            "answerMeta": {"capabilityId": "knowledge.metric_explain", "answerSource": "LLM"},
            "toolResults": [
                {"toolName": "knowledge.retrieve", "success": True},
                {"toolName": "gis.queryRegionSummary", "success": True},
            ],
        }

        issues = validate_case_response(case, response)

        self.assertIn("prohibited tool executed: gis.queryRegionSummary", issues)

    def test_solution_cases_expect_concrete_capabilities(self):
        samples = {
            "projectId": "project-1",
            "route": {"objectType": "ROAD_ROUTE", "objectId": "route-1", "routeCode": "Y016140727"},
            "section": {"objectType": "ROAD_SECTION", "objectId": "section-1", "routeCode": "Y016140727", "startStake": 1, "endStake": 2},
            "disease": {"objectType": "DISEASE", "objectId": "disease-1", "routeCode": "Y016140727", "startStake": 1.2, "endStake": 1.2},
            "assessment": {"objectType": "ASSESSMENT_RESULT", "objectId": "assessment-1", "routeCode": "Y016140727", "year": 2026, "startStake": 1, "endStake": 1.1},
            "geometry": {"type": "Polygon", "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.2], [112.1, 37.1]]]},
        }

        generation_cases = {
            case.case_id: case
            for case in build_acceptance_cases(samples, require_ai=True)
            if case.generation
        }

        self.assertEqual("solution.route_report", generation_cases["solution.route_report"].expected_capability)
        self.assertEqual("solution.section_plan", generation_cases["solution.section_plan"].expected_capability)
        self.assertEqual("solution.disease_review", generation_cases["solution.disease_review"].expected_capability)
        self.assertEqual("solution.assessment_advice", generation_cases["solution.assessment_advice"].expected_capability)
        self.assertEqual("solution.region_advice", generation_cases["solution.region_advice"].expected_capability)

    def test_requires_answer_meta_for_all_live_cases(self):
        case = AcceptanceCase(case_id="map.route_analysis", name="分析路线", payload={}, required_tools=["gis.queryDiseases"])
        response = {"answer": "已分析。", "toolResults": [{"toolName": "gis.queryDiseases", "success": True}]}

        issues = validate_case_response(case, response)

        self.assertIn("missing answerMeta", issues)

    def test_business_source_must_be_locatable_when_case_requires_it(self):
        case = AcceptanceCase(
            case_id="map.route_analysis",
            name="分析路线",
            payload={},
            required_tools=["gis.queryDiseases"],
            require_locatable_business_sources=True,
        )
        response = {
            "answer": "已分析。",
            "answerMeta": {"capabilityId": "map.route_analysis", "answerSource": "LLM"},
            "toolResults": [{"toolName": "gis.queryDiseases", "success": True}],
            "sources": [
                {"sourceType": "BUSINESS_DATA", "sourceTitle": "病害记录", "content": "裂缝"},
                {"sourceType": "KNOWLEDGE", "sourceTitle": "规范条文", "content": "PCI 指标说明"},
            ],
        }

        issues = validate_case_response(case, response)

        self.assertIn("business source is not locatable: 病害记录", issues)

    def test_rejects_sources_without_standard_binding_contract(self):
        case = AcceptanceCase(
            case_id="map.route_analysis",
            name="分析路线",
            payload={},
        )
        response = {
            "answer": "已分析。",
            "answerMeta": {"capabilityId": "map.route_analysis", "answerSource": "LLM"},
            "sources": [
                {
                    "sourceType": "KNOWLEDGE",
                    "sourceTitle": "PCI 指标说明",
                    "routeCode": "Y016140727",
                },
                {
                    "sourceType": "BUSINESS_DATA",
                    "sourceTitle": "病害记录",
                    "bindingType": "OBJECT",
                    "bindingStatus": "UNVERIFIED",
                },
            ],
        }

        issues = validate_case_response(case, response)

        self.assertIn("source binding contract invalid: PCI 指标说明", issues)
        self.assertIn("source binding contract invalid: 病害记录", issues)

    def test_validates_knowledge_sources_when_primary_sources_list_is_empty(self):
        case = AcceptanceCase(
            case_id="knowledge.metric_explain",
            name="解释 PCI 指标",
            payload={},
        )
        response = {
            "answer": "PCI 是路面损坏状况指数。",
            "answerMeta": {"capabilityId": "knowledge.metric_explain", "answerSource": "LLM"},
            "sources": [],
            "knowledgeSources": [
                {
                    "sourceType": "KNOWLEDGE",
                    "sourceTitle": "未标准化知识来源",
                }
            ],
        }

        issues = validate_case_response(case, response)

        self.assertIn("source binding contract invalid: 未标准化知识来源", issues)

    def test_plain_reference_followup_uses_only_knowledge_tool(self):
        case = AcceptanceCase(
            case_id="followup.source_replay",
            name="Replay plain reference",
            payload={},
            followup_replay=True,
        )
        followup_source = {
            "sourceType": "KNOWLEDGE",
            "sourceTitle": "PCI 指标说明",
            "bindingType": "NONE",
            "bindingOrigin": "NONE",
            "bindingStatus": "VALID",
            "bindingReason": "来源未绑定地图，仅作为参考资料",
        }
        response = {
            "answer": "PCI 用于评价路面损坏状况。",
            "answerMeta": {"capabilityId": "knowledge.metric_explain", "answerSource": "LLM"},
            "toolResults": [{"toolName": "knowledge.retrieve", "success": True}],
            "planExecution": {
                "plannedToolNames": ["knowledge.retrieve"],
                "actualToolNames": ["knowledge.retrieve"],
            },
            "mapContext": {"followupSource": followup_source},
        }

        issues = validate_case_response(case, response)

        self.assertEqual([], issues)
        self.assertEqual(["knowledge.retrieve"], acceptance.extract_planned_tool_names(response))
        self.assertEqual(["knowledge.retrieve"], acceptance.extract_actual_tool_names(response))

    def test_plain_reference_followup_rejects_gis_tool(self):
        case = AcceptanceCase(
            case_id="followup.source_replay",
            name="Replay plain reference",
            payload={},
            followup_replay=True,
        )
        followup_source = {
            "sourceType": "KNOWLEDGE",
            "sourceTitle": "PCI 指标说明",
            "bindingType": "NONE",
            "bindingOrigin": "NONE",
            "bindingStatus": "VALID",
        }
        response = {
            "answer": "继续分析。",
            "answerMeta": {"capabilityId": "knowledge.metric_explain", "answerSource": "LLM"},
            "toolResults": [
                {"toolName": "knowledge.retrieve", "success": True},
                {"toolName": "gis.queryNearbyObjects", "success": True},
            ],
            "planExecution": {
                "plannedToolNames": ["knowledge.retrieve", "gis.queryNearbyObjects"],
                "actualToolNames": ["knowledge.retrieve", "gis.queryNearbyObjects"],
            },
            "mapContext": {"followupSource": followup_source},
        }

        issues = validate_case_response(case, response)

        self.assertIn("plain reference follow-up planned GIS tool: gis.queryNearbyObjects", issues)
        self.assertIn("plain reference follow-up executed GIS tool: gis.queryNearbyObjects", issues)

    def test_disease_object_followup_requires_nearby_objects_tool(self):
        case = AcceptanceCase(
            case_id="followup.source_replay",
            name="Replay disease source",
            payload={},
            followup_replay=True,
            expected_followup_map_target={
                "objectType": "DISEASE",
                "objectId": "disease-1",
            },
        )
        followup_source = {
            "sourceType": "BUSINESS_DATA",
            "sourceTitle": "病害记录",
            "bindingType": "OBJECT",
            "bindingOrigin": "BUSINESS_QUERY",
            "bindingStatus": "UNVERIFIED",
            "mapTarget": {
                "objectType": "DISEASE",
                "objectId": "disease-1",
                "routeCode": "Y016140727",
            },
        }
        response = {
            "answer": "继续分析。",
            "answerMeta": {"capabilityId": "map.disease_analysis", "answerSource": "LLM"},
            "toolResults": [
                {"toolName": "gis.queryNearbyObjects", "success": True},
                {"toolName": "knowledge.retrieve", "success": True},
            ],
            "planExecution": {
                "plannedToolNames": ["gis.queryNearbyObjects", "knowledge.retrieve"],
                "actualToolNames": ["gis.queryNearbyObjects", "knowledge.retrieve"],
            },
            "mapContext": {"followupSource": followup_source},
        }

        issues = validate_case_response(case, response)

        self.assertEqual([], issues)
        self.assertIn("gis.queryNearbyObjects", acceptance.extract_planned_tool_names(response))
        self.assertIn("gis.queryNearbyObjects", acceptance.extract_actual_tool_names(response))

    def test_source_followup_context_is_not_rebuilt_from_top_level_fields(self):
        followup_context = {
            "sourceId": "chunk-1",
            "sourceType": "KNOWLEDGE",
            "sourceTitle": "PCI 指标说明",
            "bindingType": "NONE",
            "bindingStatus": "VALID",
        }
        source = {
            "sourceId": "chunk-1",
            "sourceType": "KNOWLEDGE",
            "sourceTitle": "PCI 指标说明",
            "bindingType": "OBJECT",
            "bindingStatus": "UNVERIFIED",
            "mapTarget": {
                "objectType": "DISEASE",
                "objectId": "must-not-inherit",
            },
            "followupContext": followup_context,
        }

        self.assertEqual(followup_context, acceptance.source_followup_context(source))

    def test_generation_rejects_template_fallback_missing_variables_and_mustache(self):
        case = AcceptanceCase(
            case_id="solution.section_plan",
            name="生成路段养护计划",
            payload={},
            required_tools=["solution.generateDraft"],
            generation=True,
        )
        response = {
            "answer": "# {{routeCode}} 养护计划\n变量缺失：unitCode",
            "answerMeta": {"capabilityId": "solution.generate", "answerSource": "TEMPLATE"},
            "toolResults": [
                {
                    "toolName": "solution.generateDraft",
                    "success": True,
                    "data": {
                        "fallback": True,
                        "missingVariables": ["unitCode"],
                        "markdown": "# {{routeCode}} 养护计划",
                    },
                }
            ],
        }

        issues = validate_case_response(case, response)

        self.assertIn("generation used fallback template", issues)
        self.assertIn("generation missing variables: unitCode", issues)
        self.assertIn("unrendered template variable found", issues)

    def test_build_followup_replay_case_injects_source_context(self):
        builder = getattr(acceptance, "build_followup_replay_case", None)
        self.assertIsNotNone(builder, "missing build_followup_replay_case")
        source_target = {
            "objectType": "DISEASE",
            "objectId": "disease-1",
            "routeCode": "Y016140727",
            "startStake": 1.2,
            "endStake": 1.2,
        }
        followup_context = {
            "sourceId": "disease-1",
            "sourceType": "BUSINESS_DATA",
            "sourceTitle": "病害记录",
            "contentExcerpt": "K1+200 裂缝",
            "bindingType": "OBJECT",
            "bindingStatus": "UNVERIFIED",
            "mapTarget": source_target,
        }
        parent = CaseRunResult(
            case=AcceptanceCase(
                case_id="map.disease_analysis",
                name="Analyze disease",
                payload={
                    "action": "ANALYZE_OBJECT",
                    "message": "分析当前病害",
                    "mapContext": {
                        "mode": "OBJECT",
                        "routeCode": "Y016140727",
                        "extra": {"rawContext": {"query": {"projectId": "project-1"}}},
                    },
                    "options": {"useBusinessData": True, "useKnowledge": True},
                },
            ),
            response={
                "answer": "已分析。",
                "answerMeta": {"capabilityId": "map.disease_analysis"},
                "sources": [
                    {
                        "sourceType": "BUSINESS_DATA",
                        "sourceTitle": "病害记录",
                        "bindingType": "OBJECT",
                        "bindingOrigin": "BUSINESS_QUERY",
                        "bindingStatus": "UNVERIFIED",
                        "mapTarget": source_target,
                        "followupContext": followup_context,
                    }
                ],
            },
            issues=[],
            elapsed_ms=12,
        )

        replay = builder(parent, require_ai=True, top_k=3)

        self.assertIsNotNone(replay)
        self.assertEqual("followup.source_replay", replay.case_id)
        self.assertEqual("CHAT", replay.payload["action"])
        self.assertTrue(replay.followup_replay)
        self.assertEqual(source_target, replay.expected_followup_map_target)
        replay_followup = replay.payload["mapContext"]["followupSource"]
        self.assertEqual(source_target, replay_followup["mapTarget"])
        self.assertEqual("病害记录", replay_followup["sourceTitle"])
        self.assertEqual("BUSINESS_DATA", replay_followup["sourceType"])
        self.assertEqual(replay_followup, replay.payload["mapContext"]["extra"]["followupSource"])
        self.assertEqual(replay_followup, replay.payload["mapContext"]["extra"]["rawContext"]["followupSource"])
        self.assertEqual(["gis.queryNearbyObjects", "knowledge.retrieve"], replay.required_tools)
        self.assertEqual(3, replay.payload["options"]["topK"])
        self.assertTrue(replay.payload["options"]["requireAi"])

    def test_followup_replay_requires_echoed_locatable_source_context(self):
        case = AcceptanceCase(case_id="followup.source_replay", name="Replay source follow-up", payload={})
        case.followup_replay = True
        case.expected_followup_map_target = {
            "objectType": "DISEASE",
            "objectId": "disease-1",
            "routeCode": "Y016140727",
        }
        response = {
            "answer": "继续分析。",
            "answerMeta": {"capabilityId": "map.disease_analysis", "answerSource": "LLM"},
            "toolResults": [{"toolName": "knowledge.retrieve", "success": True}],
            "mapContext": {"extra": {}},
        }

        issues = validate_case_response(case, response)

        self.assertIn("missing echoed followupSource", issues)


if __name__ == "__main__":
    unittest.main()
