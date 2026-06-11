import unittest

from app.evidence_snapshot import EvidenceSnapshotStore, build_scope_fingerprint


class EvidenceSnapshotTest(unittest.TestCase):
    def test_scope_fingerprint_is_stable_for_equivalent_context(self):
        first = {
            "mode": "OBJECT",
            "routeCode": "Y016140727",
            "mapObject": {"objectType": "ROAD_SECTION", "objectId": "section-1", "startStake": 0, "endStake": 14.072},
            "extra": {"rawContext": {"query": {"projectId": "project-1", "indexCode": "MQI"}}},
            "selectedLayers": ["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"],
        }
        second = {
            "selectedLayers": ["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"],
            "extra": {"rawContext": {"query": {"indexCode": "MQI", "projectId": "project-1"}}},
            "mapObject": {"endStake": 14.072, "startStake": 0, "objectId": "section-1", "objectType": "ROAD_SECTION"},
            "routeCode": "Y016140727",
            "mode": "OBJECT",
        }

        self.assertEqual(build_scope_fingerprint(first), build_scope_fingerprint(second))

    def test_store_returns_snapshot_only_when_scope_matches_and_not_expired(self):
        store = EvidenceSnapshotStore(ttl_seconds=60)
        context = {"mode": "ROUTE", "routeCode": "Y016140727", "extra": {"rawContext": {"query": {"projectId": "project-1"}}}}
        snapshot = store.create(
            analysis_trace_id="trace-1",
            capability_id="map.route_analysis",
            map_context=context,
            tool_results=[{"toolName": "gis.queryDiseases", "success": True}],
            sources=[{"sourceType": "BUSINESS_DATA", "mapTarget": {"routeCode": "Y016140727"}}],
            evidence={"businessHitCount": 1},
            now_ms=1000,
        )

        self.assertEqual(
            snapshot["evidenceSnapshotId"],
            store.get(snapshot["evidenceSnapshotId"], context, now_ms=2000)["evidenceSnapshotId"],
        )
        self.assertIsNone(store.get(snapshot["evidenceSnapshotId"], {"mode": "ROUTE", "routeCode": "G210"}, now_ms=2000))
        self.assertIsNone(store.get(snapshot["evidenceSnapshotId"], context, now_ms=70000))


if __name__ == "__main__":
    unittest.main()
