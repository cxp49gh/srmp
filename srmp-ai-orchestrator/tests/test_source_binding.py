import copy
import json
import unittest

from app.source_binding import normalize_source, normalize_sources


class SourceBindingNormalizerTest(unittest.TestCase):
    def test_business_object_is_unverified_object_binding(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "sourceId": "disease-1",
                "sourceTitle": "裂缝",
                "content": "K1+200 处纵向裂缝",
                "objectType": "DISEASE",
                "objectId": "disease-1",
                "routeCode": "Y016140727",
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual("BUSINESS_QUERY", source["bindingOrigin"])
        self.assertEqual("UNVERIFIED", source["bindingStatus"])
        self.assertEqual("disease-1", source["mapTarget"]["objectId"])
        self.assertEqual("K1+200 处纵向裂缝", source["contentExcerpt"])
        self.assertEqual(source["mapTarget"], source["followupContext"]["mapTarget"])

    def test_object_binding_takes_priority_over_range(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "objectType": "road_section",
                "objectId": "section-1",
                "routeCode": "Y016140727",
                "startStake": 1.2,
                "endStake": 1.4,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual("ROAD_SECTION", source["mapTarget"]["objectType"])
        self.assertEqual(1.2, source["mapTarget"]["startStake"])

    def test_business_route_stake_is_unverified_range_binding(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "routeCode": "Y016140727",
                "startStake": 1.2,
                "endStake": 1.4,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual("BUSINESS_QUERY", source["bindingOrigin"])
        self.assertEqual("UNVERIFIED", source["bindingStatus"])

    def test_zero_stake_range_is_preserved(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "routeCode": "Y016140727",
                "startStake": 0,
                "endStake": 0,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual(0, source["mapTarget"]["startStake"])
        self.assertEqual(0, source["mapTarget"]["endStake"])

    def test_numeric_string_stake_range_is_valid(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "routeCode": "Y016140727",
                "startStake": "1.2",
                "endStake": "1.4",
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])

    def test_invalid_route_code_rejects_route_only_binding(self):
        for route_code in (123, True, "   "):
            with self.subTest(route_code=route_code):
                source = normalize_source(
                    {
                        "sourceType": "BUSINESS_DATA",
                        "routeCode": route_code,
                        "startStake": 1.2,
                        "endStake": 1.4,
                    },
                    origin="BUSINESS_QUERY",
                )

                self.assertEqual("NONE", source["bindingType"])
                self.assertEqual("INVALID", source["bindingStatus"])
                self.assertIn("routeCode", source["bindingReason"])

    def test_route_code_is_stripped_for_valid_range(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "routeCode": "  Y016140727  ",
                "startStake": 1.2,
                "endStake": 1.4,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual("Y016140727", source["mapTarget"]["routeCode"])

    def test_main_bindings_remove_invalid_route_code_group(self):
        cases = [
            {
                "objectType": "ROAD_SECTION",
                "objectId": "section-1",
                "routeCode": 123,
            },
            {
                "geometry": {"type": "Point", "coordinates": [112.1, 37.1]},
                "routeCode": True,
            },
            {
                "bbox": [112.1, 37.1, 112.2, 37.2],
                "routeCode": "   ",
            },
        ]

        for binding in cases:
            with self.subTest(binding=binding):
                source = normalize_source(
                    {
                        "sourceType": "BUSINESS_DATA",
                        **binding,
                        "startStake": 1.2,
                        "endStake": 1.4,
                    },
                    origin="BUSINESS_QUERY",
                )

                self.assertIn(source["bindingType"], {"OBJECT", "RANGE"})
                self.assertTrue(
                    {"routeCode", "startStake", "endStake"}.isdisjoint(
                        source["mapTarget"]
                    )
                )

    def test_invalid_stake_values_are_rejected(self):
        cases = [
            (True, 1, "startStake"),
            (0, False, "endStake"),
            (float("nan"), 1, "startStake"),
            (0, float("inf"), "endStake"),
            ("not-a-number", 1, "startStake"),
        ]

        for start, end, reason_field in cases:
            with self.subTest(start=start, end=end):
                source = normalize_source(
                    {
                        "sourceType": "BUSINESS_DATA",
                        "routeCode": "Y016140727",
                        "startStake": start,
                        "endStake": end,
                    },
                    origin="BUSINESS_QUERY",
                )
                self.assertEqual("NONE", source["bindingType"])
                self.assertEqual("INVALID", source["bindingStatus"])
                self.assertIn(reason_field, source["bindingReason"])
                json.dumps(source, allow_nan=False)

    def test_reversed_stake_range_is_invalid(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "routeCode": "Y016140727",
                "startStake": 2,
                "endStake": 1,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("NONE", source["bindingType"])
        self.assertEqual("INVALID", source["bindingStatus"])
        self.assertIn("startStake", source["bindingReason"])
        self.assertIn("endStake", source["bindingReason"])

    def test_object_binding_ignores_invalid_auxiliary_range(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "objectType": "ROAD_SECTION",
                "objectId": "section-1",
                "routeCode": "Y016140727",
                "startStake": float("nan"),
                "endStake": float("inf"),
                "geometry": {
                    "type": "Point",
                    "coordinates": [112.1, float("nan")],
                },
                "bbox": [112.1, 37.1, float("inf"), 37.2],
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual("UNVERIFIED", source["bindingStatus"])
        self.assertTrue(
            {
                "routeCode",
                "startStake",
                "endStake",
                "geometry",
                "bbox",
            }.isdisjoint(source["mapTarget"])
        )
        json.dumps(source, allow_nan=False)

    def test_geometry_is_unverified_range_binding(self):
        geometry = {"type": "Point", "coordinates": [112.1, 37.1]}
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "mapTarget": {"objectType": "MAP_REGION", "geometry": geometry},
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual(geometry, source["mapTarget"]["geometry"])
        self.assertEqual("MAP_REGION", source["mapTarget"]["objectType"])

    def test_geometry_range_removes_invalid_auxiliary_route_stake_group(self):
        cases = [
            (float("nan"), 1),
            (0, float("inf")),
            (True, 1),
            ("invalid", 1),
            (1, None),
            (2, 1),
        ]

        for start, end in cases:
            with self.subTest(start=start, end=end):
                raw = {
                    "sourceType": "BUSINESS_DATA",
                    "geometry": {"type": "Point", "coordinates": [112.1, 37.1]},
                    "routeCode": "Y016140727",
                    "startStake": start,
                }
                if end is not None:
                    raw["endStake"] = end

                source = normalize_source(raw, origin="BUSINESS_QUERY")

                self.assertEqual("RANGE", source["bindingType"])
                self.assertTrue(
                    {"routeCode", "startStake", "endStake"}.isdisjoint(
                        source["mapTarget"]
                    )
                )
                json.dumps(source, allow_nan=False)

    def test_geometry_range_preserves_valid_auxiliary_route_stake_group(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "geometry": {"type": "Point", "coordinates": [112.1, 37.1]},
                "routeCode": "Y016140727",
                "startStake": "1.2",
                "endStake": "1.4",
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual("Y016140727", source["mapTarget"]["routeCode"])
        self.assertEqual(1.2, source["mapTarget"]["startStake"])
        self.assertEqual(1.4, source["mapTarget"]["endStake"])

    def test_malformed_geometry_is_invalid(self):
        cases = [
            ("Point", "geometry"),
            ({}, "geometry.type"),
            ({"type": "", "coordinates": [112.1, 37.1]}, "geometry.type"),
            (
                {"type": "CircularString", "coordinates": [[112.1, 37.1]]},
                "geometry.type",
            ),
            ({"type": "Point", "coordinates": [112.1]}, "geometry.coordinates"),
            (
                {"type": "Point", "coordinates": ["112.1", 37.1]},
                "geometry.coordinates",
            ),
            (
                {"type": "Point", "coordinates": [112.1, float("nan")]},
                "geometry.coordinates",
            ),
            ({"type": "LineString", "coordinates": "invalid"}, "geometry.coordinates"),
            ({"type": "LineString", "coordinates": []}, "geometry.coordinates"),
            (
                {
                    "type": "LineString",
                    "coordinates": [[112.1, 37.1], [float("nan"), 37.2]],
                },
                "geometry.coordinates",
            ),
            (
                {"type": "LineString", "coordinates": [[112.1, 37.1]]},
                "geometry.coordinates",
            ),
            (
                {
                    "type": "Polygon",
                    "coordinates": [[[112.1, 37.1], [112.2, True]]],
                },
                "geometry.coordinates",
            ),
            (
                {
                    "type": "Polygon",
                    "coordinates": [
                        [[112.1, 37.1], [112.2, 37.1], [112.1, 37.1]]
                    ],
                },
                "geometry.coordinates",
            ),
            (
                {
                    "type": "Polygon",
                    "coordinates": [
                        [
                            [112.1, 37.1],
                            [112.2, 37.1],
                            [112.2, 37.2],
                            [112.1, 37.2],
                        ]
                    ],
                },
                "geometry.coordinates",
            ),
            (
                {"type": "GeometryCollection", "geometries": []},
                "geometry.geometries",
            ),
            (
                {"type": "GeometryCollection", "geometries": "invalid"},
                "geometry.geometries",
            ),
            (
                {
                    "type": "GeometryCollection",
                    "geometries": [
                        {
                            "type": "LineString",
                            "coordinates": [[112.1, 37.1], [float("inf"), 37.2]],
                        }
                    ],
                },
                "geometry.geometries",
            ),
        ]

        for geometry, reason in cases:
            with self.subTest(geometry=geometry):
                source = normalize_source(
                    {"sourceType": "BUSINESS_DATA", "geometry": geometry},
                    origin="BUSINESS_QUERY",
                )
                self.assertEqual("NONE", source["bindingType"])
                self.assertEqual("INVALID", source["bindingStatus"])
                self.assertIn(reason, source["bindingReason"])
                json.dumps(source, allow_nan=False)

    def test_standard_nested_geojson_types_are_valid_ranges(self):
        geometries = [
            {"type": "Point", "coordinates": [112.1, 37.1, 450.0]},
            {
                "type": "LineString",
                "coordinates": [[112.1, 37.1], [112.2, 37.2]],
            },
            {
                "type": "Polygon",
                "coordinates": [
                    [
                        [112.1, 37.1],
                        [112.2, 37.1],
                        [112.2, 37.2],
                        [112.1, 37.1],
                    ]
                ],
            },
            {
                "type": "MultiPolygon",
                "coordinates": [
                    [
                        [
                            [112.1, 37.1],
                            [112.2, 37.1],
                            [112.2, 37.2],
                            [112.1, 37.1],
                        ]
                    ]
                ],
            },
            {
                "type": "GeometryCollection",
                "geometries": [
                    {"type": "Point", "coordinates": [112.1, 37.1]},
                    {
                        "type": "LineString",
                        "coordinates": [[112.1, 37.1], [112.2, 37.2]],
                    },
                ],
            },
        ]

        for geometry in geometries:
            with self.subTest(geometry_type=geometry["type"]):
                source = normalize_source(
                    {"sourceType": "BUSINESS_DATA", "geometry": geometry},
                    origin="BUSINESS_QUERY",
                )
                self.assertEqual("RANGE", source["bindingType"])
                self.assertEqual(geometry, source["mapTarget"]["geometry"])
                json.dumps(source, allow_nan=False)

    def test_bbox_is_unverified_range_binding(self):
        bbox = [112.1, 37.1, 112.2, 37.2]
        source = normalize_source(
            {"sourceType": "BUSINESS_DATA", "bbox": bbox},
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual(bbox, source["mapTarget"]["bbox"])

    def test_bbox_range_removes_invalid_auxiliary_route_stake_group(self):
        cases = [
            (float("nan"), 1),
            (0, float("inf")),
            (False, 1),
            ("invalid", 1),
            (1, None),
            (2, 1),
        ]

        for start, end in cases:
            with self.subTest(start=start, end=end):
                raw = {
                    "sourceType": "BUSINESS_DATA",
                    "bbox": [112.1, 37.1, 112.2, 37.2],
                    "routeCode": "Y016140727",
                    "startStake": start,
                }
                if end is not None:
                    raw["endStake"] = end

                source = normalize_source(raw, origin="BUSINESS_QUERY")

                self.assertEqual("RANGE", source["bindingType"])
                self.assertTrue(
                    {"routeCode", "startStake", "endStake"}.isdisjoint(
                        source["mapTarget"]
                    )
                )
                json.dumps(source, allow_nan=False)

    def test_bbox_range_preserves_valid_auxiliary_route_stake_group(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "bbox": [112.1, 37.1, 112.2, 37.2],
                "routeCode": "Y016140727",
                "startStake": 1.2,
                "endStake": 1.4,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual("Y016140727", source["mapTarget"]["routeCode"])
        self.assertEqual(1.2, source["mapTarget"]["startStake"])
        self.assertEqual(1.4, source["mapTarget"]["endStake"])

    def test_bbox_tuple_is_normalized_to_json_list(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "bbox": (112.1, 37.1, 112.2, 37.2),
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("RANGE", source["bindingType"])
        self.assertEqual([112.1, 37.1, 112.2, 37.2], source["mapTarget"]["bbox"])

    def test_malformed_bbox_is_invalid(self):
        cases = [
            ("112,37,113,38", "bbox"),
            ([112.1, 37.1, 112.2], "bbox"),
            ([True, 37.1, 112.2, 37.2], "bbox"),
            (["112.1", 37.1, 112.2, 37.2], "bbox"),
            ([112.1, 37.1, float("inf"), 37.2], "bbox"),
            ([113, 37, 112, 38], "bbox"),
            ([112, 39, 113, 38], "bbox"),
        ]

        for bbox, reason in cases:
            with self.subTest(bbox=bbox):
                source = normalize_source(
                    {"sourceType": "BUSINESS_DATA", "bbox": bbox},
                    origin="BUSINESS_QUERY",
                )
                self.assertEqual("NONE", source["bindingType"])
                self.assertEqual("INVALID", source["bindingStatus"])
                self.assertIn(reason, source["bindingReason"])
                json.dumps(source, allow_nan=False)

    def test_plain_knowledge_does_not_inherit_external_map_target(self):
        source = normalize_source(
            {
                "sourceType": "KNOWLEDGE",
                "sourceId": "chunk-1",
                "sourceTitle": "PCI 指标说明",
                "contentExcerpt": "PCI 用于评价路面损坏状况。",
                "objectType": "DISEASE",
                "objectId": "root-object",
                "mapTarget": {
                    "objectType": "DISEASE",
                    "objectId": "request-object",
                },
                "metadata": {},
            },
            origin="EXPLICIT_METADATA",
        )

        self.assertEqual("NONE", source["bindingType"])
        self.assertEqual("NONE", source["bindingOrigin"])
        self.assertEqual("VALID", source["bindingStatus"])
        self.assertNotIn("mapTarget", source)
        self.assertNotIn("mapTarget", source["followupContext"])

    def test_knowledge_uses_only_explicit_metadata(self):
        source = normalize_source(
            {
                "sourceType": "KNOWLEDGE",
                "sourceId": "doc-1",
                "metadata": {
                    "mapTarget": {
                        "objectType": "assessment",
                        "objectId": "assessment-1",
                        "routeCode": "Y016140727",
                    }
                },
            },
            origin="EXPLICIT_METADATA",
        )

        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual("EXPLICIT_METADATA", source["bindingOrigin"])
        self.assertEqual("ASSESSMENT_RESULT", source["mapTarget"]["objectType"])
        self.assertEqual("assessment-1", source["mapTarget"]["objectId"])

    def test_camel_case_road_section_alias_is_an_object_binding(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "objectType": "roadSection",
                "objectId": "section-1",
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual("ROAD_SECTION", source["mapTarget"]["objectType"])

    def test_object_id_without_object_type_is_invalid(self):
        source = normalize_source(
            {"sourceType": "BUSINESS_DATA", "objectId": "disease-1"},
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("NONE", source["bindingType"])
        self.assertEqual("NONE", source["bindingOrigin"])
        self.assertEqual("INVALID", source["bindingStatus"])
        self.assertIn("objectType", source["bindingReason"])
        self.assertNotIn("mapTarget", source)

    def test_invalid_object_type_is_invalid(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "objectType": "CURRENT_SELECTION",
                "objectId": "selection-1",
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("NONE", source["bindingType"])
        self.assertEqual("INVALID", source["bindingStatus"])
        self.assertIn("objectType", source["bindingReason"])

    def test_incomplete_route_stake_range_is_invalid(self):
        source = normalize_source(
            {
                "sourceType": "BUSINESS_DATA",
                "routeCode": "Y016140727",
                "startStake": 1.2,
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual("NONE", source["bindingType"])
        self.assertEqual("INVALID", source["bindingStatus"])
        self.assertIn("startStake", source["bindingReason"])
        self.assertIn("endStake", source["bindingReason"])

    def test_template_is_reference_only_by_default(self):
        source = normalize_source(
            {"sourceType": "TEMPLATE", "sourceTitle": "系统兜底模板"},
            origin="NONE",
        )

        self.assertEqual("NONE", source["bindingType"])
        self.assertEqual("NONE", source["bindingOrigin"])
        self.assertEqual("VALID", source["bindingStatus"])

    def test_common_object_type_aliases_are_normalized(self):
        aliases = {
            "route": "ROAD_ROUTE",
            "road-segment": "ROAD_SECTION",
            "evaluationUnit": "EVALUATION_UNIT",
            "disease_record": "DISEASE",
            "assessment_result_record": "ASSESSMENT_RESULT",
            "region": "MAP_REGION",
        }

        for alias, expected in aliases.items():
            with self.subTest(alias=alias):
                source = normalize_source(
                    {
                        "sourceType": "BUSINESS_DATA",
                        "objectType": alias,
                        "objectId": "object-1",
                    },
                    origin="BUSINESS_QUERY",
                )
                self.assertEqual(expected, source["mapTarget"]["objectType"])

    def test_followup_context_is_rebuilt_from_normalized_fields(self):
        source = normalize_source(
            {
                "source_id": "route-1",
                "source_type": "business_data",
                "source_title": "路线",
                "content_excerpt": "路线资料",
                "objectType": "ROAD_ROUTE",
                "objectId": "route-1",
                "followupContext": {"sourceTitle": "旧标题", "unsafe": True},
            },
            origin="BUSINESS_QUERY",
        )

        self.assertEqual(
            {
                "sourceId": "route-1",
                "sourceType": "BUSINESS_DATA",
                "sourceTitle": "路线",
                "contentExcerpt": "路线资料",
                "bindingType": "OBJECT",
                "bindingStatus": "UNVERIFIED",
                "mapTarget": source["mapTarget"],
            },
            source["followupContext"],
        )

    def test_output_nested_values_are_deeply_isolated(self):
        raw = {
            "sourceType": "BUSINESS_DATA",
            "metadata": {"labels": ["original"]},
            "mapTarget": {
                "objectType": "MAP_REGION",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [[112.1, 37.1], [112.2, 37.2]],
                },
            },
        }

        source = normalize_source(raw, origin="BUSINESS_QUERY")

        self.assertIsNot(raw["metadata"], source["metadata"])
        self.assertIsNot(source["mapTarget"], source["followupContext"]["mapTarget"])
        source["metadata"]["labels"].append("changed")
        source["mapTarget"]["geometry"]["coordinates"][0][0] = 0
        self.assertEqual(["original"], raw["metadata"]["labels"])
        self.assertEqual(112.1, raw["mapTarget"]["geometry"]["coordinates"][0][0])
        self.assertEqual(
            112.1,
            source["followupContext"]["mapTarget"]["geometry"]["coordinates"][0][0],
        )

    def test_output_removes_legacy_snake_case_contract_fields(self):
        legacy_keys = {
            "source_type",
            "source_id",
            "source_title",
            "content_excerpt",
            "map_target",
            "followup_context",
            "binding_type",
            "binding_origin",
            "binding_status",
            "binding_reason",
        }
        source = normalize_source(
            {
                "source_type": "business_data",
                "source_id": "section-1",
                "source_title": "路段",
                "content_excerpt": "路段资料",
                "map_target": {
                    "objectType": "ROAD_SECTION",
                    "objectId": "section-1",
                },
                "followup_context": {"unsafe": True},
                "binding_type": "NONE",
                "binding_origin": "NONE",
                "binding_status": "VALID",
                "binding_reason": "legacy",
                "metadata": {"keep": True},
            },
            origin="BUSINESS_QUERY",
        )

        self.assertTrue(legacy_keys.isdisjoint(source))
        self.assertEqual("BUSINESS_DATA", source["sourceType"])
        self.assertEqual("section-1", source["sourceId"])
        self.assertEqual("OBJECT", source["bindingType"])
        self.assertEqual({"keep": True}, source["metadata"])

    def test_normalize_sources_skips_non_dict_values(self):
        sources = normalize_sources(
            [
                {"sourceType": "TEMPLATE", "sourceTitle": "模板"},
                None,
                "invalid",
            ],
            origin="NONE",
        )

        self.assertEqual(1, len(sources))
        self.assertEqual("VALID", sources[0]["bindingStatus"])

    def test_normalization_does_not_modify_input(self):
        raw = {
            "sourceType": "BUSINESS_DATA",
            "mapTarget": {
                "objectType": "DISEASE",
                "objectId": "disease-1",
            },
            "followupContext": {"mapTarget": {"objectId": "old"}},
        }
        original = copy.deepcopy(raw)

        normalize_source(raw, origin="BUSINESS_QUERY")

        self.assertEqual(original, raw)


if __name__ == "__main__":
    unittest.main()
