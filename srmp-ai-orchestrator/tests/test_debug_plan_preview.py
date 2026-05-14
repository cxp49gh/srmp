import unittest

from fastapi.testclient import TestClient

from app.main import app


class DebugPlanPreviewApiTest(unittest.TestCase):
    def test_region_solution_plan_returns_action_sources_and_enriched_tools(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/debug/plan",
            json={
                "action": "GENERATE_REGION_SOLUTION",
                "message": "生成框选区域养护建议",
                "mapContext": {
                    "tenantId": "default",
                    "mode": "REGION",
                    "routeCode": "G210",
                    "year": 2026,
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[106.0, 25.8], [106.2, 25.8], [106.2, 26.0], [106.0, 25.8]]],
                    },
                    "regionSummary": {
                        "routeCount": 1,
                        "sectionCount": 4,
                        "diseaseSummary": {"disease_count": 59},
                    },
                    "selectedLayers": ["ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
                },
                "actionInput": {"solutionType": "REGION_MAINTENANCE_SUGGESTION"},
                "options": {"useKnowledge": True, "topK": 5, "traceId": "plan-region-1"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("plan-region-1", body["traceId"])
        self.assertEqual("GENERATE_REGION_SOLUTION", body["action"])
        self.assertIn(body["intent"], {"REGION_ANALYSIS", "SOLUTION_GENERATE"})
        self.assertIsInstance(body["contextSummary"], dict)
        self.assertIsInstance(body["toolPlan"], list)
        self.assertTrue(body["toolPlan"])
        self.assertTrue(all("label" in item for item in body["toolPlan"]))
        self.assertTrue(all("readOnly" in item for item in body["toolPlan"]))
        self.assertTrue(all("writeRisk" in item for item in body["toolPlan"]))
        self.assertFalse(any(item["writeRisk"] for item in body["toolPlan"]))
        self.assertTrue(any(item["sourceType"] == "BUSINESS_DATA" for item in body["sourceHints"]))
        self.assertTrue(any(item["sourceType"] == "KNOWLEDGE" for item in body["sourceHints"]))
        self.assertTrue(any(item["sourceType"] == "MAP_REGION" for item in body["sourceHints"]))
        self.assertEqual([], [item for item in body["warnings"] if item["code"] == "REGION_GEOMETRY_MISSING"])

    def test_region_plan_warns_when_geometry_is_missing(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/debug/plan",
            json={
                "action": "ANALYZE_REGION",
                "message": "分析当前区域",
                "mapContext": {
                    "tenantId": "default",
                    "mode": "REGION",
                    "routeCode": "G210",
                    "year": 2026,
                    "regionSummary": {"routeCount": 1},
                },
                "options": {"useKnowledge": False, "traceId": "plan-region-missing-geometry"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        warning_codes = [item["code"] for item in body["warnings"]]
        self.assertIn("REGION_GEOMETRY_MISSING", warning_codes)
        self.assertTrue(any(item["sourceType"] == "MAP_REGION" for item in body["sourceHints"]))
