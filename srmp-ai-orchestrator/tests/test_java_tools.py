import unittest
import asyncio

from app.java_tools import JavaToolGateway, extract_business_sources, extract_knowledge_sources
from app.schemas import ToolResult


class JavaToolGatewayHeaderTest(unittest.TestCase):
    def test_headers_sanitize_non_ascii_trace_id_for_http_headers(self):
        gateway = JavaToolGateway()
        try:
            headers = gateway._headers(tenant_id="default", trace_id="codex-full-template-病害复核")
        finally:
            asyncio.run(gateway.close())

        self.assertEqual("default", headers["X-Tenant-Id"])
        self.assertEqual("codex-full-template-", headers["X-AI-Trace-Id"])
        headers["X-AI-Trace-Id"].encode("ascii")


class JavaToolSourceNormalizationTest(unittest.TestCase):
    def test_business_disease_is_standardized_unverified_object_binding(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryDiseases",
                success=True,
                summary="查询到 1 条病害记录",
                count=1,
                data={
                    "items": [
                        {
                            "id": "disease-1",
                            "route_code": "Y016140727",
                            "start_stake": 1.2,
                            "end_stake": 1.25,
                            "disease_name": "裂缝",
                            "disease_type": "CRACK",
                        }
                    ]
                },
            )
        ])

        self.assertEqual(1, len(sources))
        source = sources[0]
        self.assertEqual("OBJECT", source.get("bindingType"))
        self.assertEqual("BUSINESS_QUERY", source.get("bindingOrigin"))
        self.assertEqual("UNVERIFIED", source.get("bindingStatus"))
        self.assertEqual("DISEASE", source["mapTarget"]["objectType"])
        self.assertEqual("disease-1", source["mapTarget"]["objectId"])
        self.assertEqual("Y016140727", source["mapTarget"]["routeCode"])
        self.assertEqual(1.2, source["mapTarget"]["startStake"])
        self.assertEqual(1.25, source["mapTarget"]["endStake"])
        self.assertEqual(
            {
                "sourceId": "disease-1",
                "sourceType": "BUSINESS_DATA",
                "sourceTitle": source["sourceTitle"],
                "contentExcerpt": source["contentExcerpt"],
                "bindingType": "OBJECT",
                "bindingStatus": "UNVERIFIED",
                "mapTarget": source["mapTarget"],
            },
            source["followupContext"],
        )

    def test_assessment_source_uses_result_row_id_not_assessed_object_id(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryAssessmentResults",
                success=True,
                summary="查询到 1 条评定结果",
                count=1,
                data={
                    "items": [
                        {
                            "id": "assessment-result-1",
                            "object_type": "ROAD_SECTION_LINE",
                            "object_id": "road-section-1",
                            "route_code": "C001140727",
                            "start_stake": 0.324,
                            "end_stake": 10.972,
                            "mqi": 97.254,
                        }
                    ]
                },
            )
        ])

        self.assertEqual(1, len(sources))
        source = sources[0]
        self.assertEqual("assessment-result-1", source["sourceId"])
        self.assertEqual("ASSESSMENT_RESULT", source["mapTarget"]["objectType"])
        self.assertEqual("assessment-result-1", source["mapTarget"]["objectId"])
        self.assertEqual(
            "assessment-result-1",
            source["followupContext"]["mapTarget"]["objectId"],
        )
        self.assertEqual("road-section-1", source["raw"]["object_id"])

    def test_region_summary_source_uses_geometry_as_map_target(self):
        geometry = {"type": "Polygon", "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.1]]]}
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryRegionSummary",
                success=True,
                summary="查询到区域统计",
                count=1,
                data={"routeCode": "Y016140727", "geometry": geometry, "routeCount": 1, "diseaseSummary": {"diseaseCount": 3}},
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("RANGE", sources[0].get("bindingType"))
        self.assertEqual("BUSINESS_QUERY", sources[0].get("bindingOrigin"))
        self.assertEqual("MAP_REGION", sources[0]["mapTarget"]["objectType"])
        self.assertNotIn("routeCode", sources[0]["mapTarget"])
        self.assertEqual(geometry, sources[0]["mapTarget"]["geometry"])

    def test_region_summary_source_uses_bbox_as_map_target(self):
        bbox = [112.1, 37.1, 112.2, 37.2]
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryRegionSummary",
                success=True,
                summary="查询到区域统计",
                count=1,
                data={"bbox": bbox, "routeCount": 1, "diseaseSummary": {"diseaseCount": 3}},
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("RANGE", sources[0].get("bindingType"))
        self.assertEqual("BUSINESS_QUERY", sources[0].get("bindingOrigin"))
        self.assertEqual("MAP_REGION", sources[0]["mapTarget"]["objectType"])
        self.assertEqual(bbox, sources[0]["mapTarget"]["bbox"])

    def test_region_summary_root_location_fields_are_reference_only(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryRegionSummary",
                success=True,
                summary="查询到区域统计",
                count=1,
                data={
                    "objectType": "ROAD_SECTION",
                    "objectId": "root-section",
                    "routeCode": "Y016140727",
                    "startStake": 1.2,
                    "endStake": 1.4,
                    "routeCount": 1,
                    "diseaseSummary": {"diseaseCount": 3},
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("NONE", sources[0].get("bindingType"))
        self.assertEqual("NONE", sources[0].get("bindingOrigin"))
        self.assertEqual("VALID", sources[0].get("bindingStatus"))
        self.assertNotIn("mapTarget", sources[0])
        self.assertNotIn("mapTarget", sources[0]["followupContext"])

    def test_business_source_uses_complete_query_scope_stake_range(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryDiseasesByStakeRange",
                success=True,
                summary="查询到路线范围病害统计",
                count=0,
                data={
                    "items": [],
                    "queryScope": {
                        "routeCode": "Y016140727",
                        "startStake": 1.2,
                        "endStake": 1.4,
                    },
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("RANGE", sources[0].get("bindingType"))
        self.assertEqual("BUSINESS_QUERY", sources[0].get("bindingOrigin"))
        self.assertEqual("UNVERIFIED", sources[0].get("bindingStatus"))
        self.assertEqual("gis.queryDiseasesByStakeRange:scope", sources[0]["sourceId"])
        self.assertEqual(
            {
                "objectType": "DISEASE",
                "routeCode": "Y016140727",
                "startStake": 1.2,
                "endStake": 1.4,
            },
            sources[0]["mapTarget"],
        )

    def test_empty_items_route_only_query_scope_is_not_locatable(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryDiseasesByStakeRange",
                success=True,
                summary="查询到路线统计",
                count=0,
                data={
                    "items": [],
                    "queryScope": {"routeCode": "Y016140727"},
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertNotIn(sources[0].get("bindingType"), {"OBJECT", "RANGE"})
        self.assertEqual("NONE", sources[0].get("bindingType"))
        self.assertEqual("NONE", sources[0].get("bindingOrigin"))
        self.assertEqual("INVALID", sources[0].get("bindingStatus"))
        self.assertNotIn("mapTarget", sources[0])
        self.assertNotIn("mapTarget", sources[0]["followupContext"])

    def test_region_route_scope_becomes_one_road_route_source(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryRegionSummary",
                success=True,
                summary="查询到路线统计",
                count=1,
                data={
                    "queryScope": {
                        "projectId": "project-a",
                        "objectType": "ROAD_ROUTE",
                        "objectId": "route-1",
                        "routeCode": "C001140727",
                        "startStake": 0,
                        "endStake": 10.972,
                    },
                    "routeCount": 1,
                    "diseaseSummary": {"diseaseCount": 3},
                },
            )
        ])

        self.assertEqual(1, len(sources))
        source = sources[0]
        self.assertEqual("区域统计｜C001140727", source["sourceTitle"])
        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual("ROAD_ROUTE", source["mapTarget"]["objectType"])
        self.assertEqual("route-1", source["mapTarget"]["objectId"])

    def test_unresolved_region_route_scope_stays_non_locatable(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryRegionSummary",
                success=True,
                summary="查询到路线统计",
                count=1,
                data={
                    "queryScope": {"routeCode": "MISSING"},
                    "routeCount": 0,
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("NONE", sources[0]["bindingType"])
        self.assertNotIn("mapTarget", sources[0])

    def test_empty_items_query_scope_object_is_an_object_binding(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryNearbyObjects",
                success=True,
                summary="查询对象范围",
                count=0,
                data={
                    "items": [],
                    "objectType": "ROAD_SECTION",
                    "objectId": "root-section",
                    "queryScope": {
                        "objectType": "DISEASE",
                        "objectId": "scope-disease",
                    },
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("OBJECT", sources[0].get("bindingType"))
        self.assertEqual("BUSINESS_QUERY", sources[0].get("bindingOrigin"))
        self.assertEqual("UNVERIFIED", sources[0].get("bindingStatus"))
        self.assertEqual("scope-disease", sources[0]["sourceId"])
        self.assertEqual(
            {
                "objectType": "DISEASE",
                "objectId": "scope-disease",
            },
            sources[0]["mapTarget"],
        )

    def test_query_scope_generic_id_is_not_promoted_to_object_binding(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryDiseases",
                success=True,
                summary="查询范围无结果",
                count=0,
                data={
                    "items": [],
                    "queryScope": {
                        "id": "request-correlation-id",
                        "routeCode": "Y016140727",
                    },
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("NONE", sources[0].get("bindingType"))
        self.assertNotIn("mapTarget", sources[0])
        self.assertNotEqual("request-correlation-id", sources[0].get("sourceId"))

    def test_nearby_object_grouped_sources_are_extracted_and_locatable(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryNearbyObjects",
                success=True,
                summary="查询到周边对象 2 条",
                count=2,
                data={
                    "diseases": [
                        {"id": "disease-1", "route_code": "Y016140727", "start_stake": 1.2, "end_stake": 1.2, "disease_name": "裂缝"}
                    ],
                    "assessments": [
                        {"id": "assessment-1", "route_code": "Y016140727", "start_stake": 1.2, "end_stake": 1.3, "mqi": 82.1}
                    ],
                },
            )
        ])

        self.assertEqual(2, len(sources))
        self.assertEqual("DISEASE", sources[0]["mapTarget"]["objectType"])
        self.assertEqual("disease-1", sources[0]["mapTarget"]["objectId"])
        self.assertEqual("ASSESSMENT_RESULT", sources[1]["mapTarget"]["objectType"])
        self.assertEqual("assessment-1", sources[1]["mapTarget"]["objectId"])

    def test_plain_knowledge_ignores_root_location_and_legacy_targets(self):
        sources = extract_knowledge_sources([
            ToolResult(
                toolName="knowledge.retrieve",
                success=True,
                summary="知识库命中 1 条",
                data={
                    "hits": [
                        {
                            "id": "chunk-1",
                            "title": "路面技术状况评定标准",
                            "content": "PCI 用于描述路面损坏状况。",
                            "routeCode": "Y016140727",
                            "startStake": 0,
                            "endStake": 0.1,
                            "mapTarget": {
                                "objectType": "DISEASE",
                                "objectId": "request-object",
                            },
                            "followupContext": {
                                "mapTarget": {
                                    "objectType": "ROAD_SECTION",
                                    "objectId": "legacy-section",
                                }
                            },
                        }
                    ]
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("NONE", sources[0].get("bindingType"))
        self.assertEqual("NONE", sources[0].get("bindingOrigin"))
        self.assertEqual("VALID", sources[0].get("bindingStatus"))
        self.assertNotIn("mapTarget", sources[0])
        self.assertNotIn("mapTarget", sources[0]["followupContext"])

    def test_knowledge_sources_use_explicit_metadata_object_binding(self):
        sources = extract_knowledge_sources([
            ToolResult(
                toolName="knowledge.retrieve",
                success=True,
                summary="知识库命中 1 条",
                data={
                    "hits": [
                        {
                            "id": "chunk-1",
                            "title": "路面技术状况评定标准",
                            "content": "PCI 用于描述路面损坏状况。",
                            "metadata": {
                                "objectType": "ASSESSMENT_RESULT",
                                "objectId": "assessment-1",
                                "routeCode": "Y016140727",
                                "startStake": 0,
                                "endStake": 0.1,
                            },
                        }
                    ]
                },
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("OBJECT", sources[0].get("bindingType"))
        self.assertEqual("EXPLICIT_METADATA", sources[0].get("bindingOrigin"))
        self.assertEqual("UNVERIFIED", sources[0].get("bindingStatus"))
        self.assertEqual("ASSESSMENT_RESULT", sources[0]["mapTarget"]["objectType"])
        self.assertEqual("assessment-1", sources[0]["mapTarget"]["objectId"])
        self.assertEqual("Y016140727", sources[0]["followupContext"]["mapTarget"]["routeCode"])


if __name__ == "__main__":
    unittest.main()
