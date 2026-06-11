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
    def test_business_sources_include_map_target_and_followup_context(self):
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
        self.assertEqual("DISEASE", source["mapTarget"]["objectType"])
        self.assertEqual("disease-1", source["mapTarget"]["objectId"])
        self.assertEqual("Y016140727", source["mapTarget"]["routeCode"])
        self.assertEqual(1.2, source["mapTarget"]["startStake"])
        self.assertEqual(1.25, source["mapTarget"]["endStake"])
        self.assertEqual(source["mapTarget"], source["followupContext"]["mapTarget"])
        self.assertEqual(source["sourceTitle"], source["followupContext"]["sourceTitle"])
        self.assertIn("裂缝", source["followupContext"]["excerpt"])

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
        self.assertEqual("MAP_REGION", sources[0]["mapTarget"]["objectType"])
        self.assertEqual("Y016140727", sources[0]["mapTarget"]["routeCode"])
        self.assertEqual(geometry, sources[0]["mapTarget"]["geometry"])

    def test_region_summary_source_uses_query_scope_route_when_geometry_absent(self):
        sources = extract_business_sources([
            ToolResult(
                toolName="gis.queryRegionSummary",
                success=True,
                summary="查询到路线统计",
                count=1,
                data={"queryScope": {"routeCode": "Y016140727"}, "routeCount": 1, "diseaseSummary": {"diseaseCount": 3}},
            )
        ])

        self.assertEqual(1, len(sources))
        self.assertEqual("MAP_REGION", sources[0]["mapTarget"]["objectType"])
        self.assertEqual("Y016140727", sources[0]["mapTarget"]["routeCode"])

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

    def test_knowledge_sources_promote_metadata_map_target(self):
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
        self.assertEqual("ASSESSMENT_RESULT", sources[0]["mapTarget"]["objectType"])
        self.assertEqual("assessment-1", sources[0]["mapTarget"]["objectId"])
        self.assertEqual("Y016140727", sources[0]["followupContext"]["mapTarget"]["routeCode"])


if __name__ == "__main__":
    unittest.main()
