import copy
import math
from typing import Any, Dict, Iterable, List, Tuple


VALID_OBJECT_TYPES = {
    "ROAD_ROUTE",
    "ROAD_SECTION",
    "EVALUATION_UNIT",
    "DISEASE",
    "ASSESSMENT_RESULT",
    "MAP_REGION",
}

VALID_ORIGINS = {"BUSINESS_QUERY", "EXPLICIT_METADATA"}
VALID_GEOMETRY_TYPES = {
    "Point",
    "MultiPoint",
    "LineString",
    "MultiLineString",
    "Polygon",
    "MultiPolygon",
    "GeometryCollection",
}
LEGACY_CONTRACT_KEYS = {
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


def normalize_sources(
    sources: Iterable[Dict[str, Any]], origin: str
) -> List[Dict[str, Any]]:
    return [
        normalize_source(source, origin=origin)
        for source in sources or []
        if isinstance(source, dict)
    ]


def normalize_source(source: Dict[str, Any], origin: str) -> Dict[str, Any]:
    result = copy.deepcopy(source or {})
    source_type = _upper(
        result.get("sourceType") or result.get("source_type") or "KNOWLEDGE"
    )
    source_id = result.get("sourceId") or result.get("source_id")
    title = (
        result.get("sourceTitle")
        or result.get("source_title")
        or result.get("title")
        or "参考来源"
    )
    excerpt = (
        result.get("contentExcerpt")
        or result.get("content_excerpt")
        or result.get("content")
        or ""
    )

    candidate = _binding_candidate(result, source_type, origin)
    binding_type, status, reason = _classify(candidate)
    binding_origin = _upper(origin) if binding_type != "NONE" else "NONE"
    if binding_origin not in VALID_ORIGINS:
        binding_origin = "NONE"

    result["sourceType"] = source_type
    result["sourceTitle"] = title
    result["contentExcerpt"] = str(excerpt)[:500]
    if source_id not in (None, ""):
        result["sourceId"] = source_id
    result["bindingType"] = binding_type
    result["bindingOrigin"] = binding_origin
    result["bindingStatus"] = status
    result["bindingReason"] = reason
    _sanitize_non_finite_binding_values(result)
    for key in LEGACY_CONTRACT_KEYS:
        result.pop(key, None)
    result.pop("mapTarget", None)
    if binding_type != "NONE":
        result["mapTarget"] = copy.deepcopy(candidate)

    followup = {
        "sourceId": source_id,
        "sourceType": source_type,
        "sourceTitle": title,
        "contentExcerpt": result["contentExcerpt"],
        "bindingType": binding_type,
        "bindingStatus": status,
    }
    if binding_type != "NONE":
        followup["mapTarget"] = copy.deepcopy(candidate)
    result["followupContext"] = followup
    return result


def _binding_candidate(
    source: Dict[str, Any], source_type: str, origin: str
) -> Dict[str, Any]:
    normalized_origin = _upper(origin)
    metadata = source.get("metadata")
    metadata = metadata if isinstance(metadata, dict) else {}

    if (
        source_type in {"KNOWLEDGE", "OUTLINE", "TEMPLATE"}
        and normalized_origin == "EXPLICIT_METADATA"
    ):
        raw = _merge_explicit_target(metadata)
    elif normalized_origin == "BUSINESS_QUERY":
        raw = _merge_explicit_target(source)
    elif normalized_origin == "EXPLICIT_METADATA":
        raw = _merge_explicit_target(metadata)
    else:
        raw = {}

    candidate = _compact(
        {
            "objectType": _object_type(
                raw.get("objectType") or raw.get("object_type")
            ),
            "objectId": _text(raw.get("objectId") or raw.get("object_id")),
            "routeCode": raw.get("routeCode") or raw.get("route_code"),
            "startStake": _first_present(raw, "startStake", "start_stake"),
            "endStake": _first_present(raw, "endStake", "end_stake"),
        }
    )
    for key in ("geometry", "bbox"):
        if key in raw:
            candidate[key] = copy.deepcopy(raw[key])
    return candidate


def _merge_explicit_target(container: Dict[str, Any]) -> Dict[str, Any]:
    target = container.get("mapTarget")
    if not isinstance(target, dict):
        target = container.get("map_target")
    if isinstance(target, dict):
        return copy.deepcopy({**container, **target})
    return copy.deepcopy(container)


def _classify(target: Dict[str, Any]) -> Tuple[str, str, str]:
    object_type = target.get("objectType")
    object_id = target.get("objectId")

    if object_type and object_type not in VALID_OBJECT_TYPES:
        return "NONE", "INVALID", "objectType 不在允许列表中"
    if object_id and not object_type:
        return "NONE", "INVALID", "objectId 必须与合法 objectType 同时提供"
    route_status, route_reason = _normalize_auxiliary_route_range(target)
    if object_type and object_id:
        _normalize_object_auxiliary_spatial_range(target)
        return "OBJECT", "UNVERIFIED", "对象绑定尚未按当前项目验证"
    has_geometry = "geometry" in target
    has_bbox = "bbox" in target
    if has_geometry:
        geometry_valid, geometry, geometry_reason = _validate_geometry(
            target.get("geometry")
        )
        if not geometry_valid:
            return "NONE", "INVALID", geometry_reason
        target["geometry"] = geometry
    if has_bbox:
        bbox_valid, bbox, bbox_reason = _validate_bbox(target.get("bbox"))
        if not bbox_valid:
            return "NONE", "INVALID", bbox_reason
        target["bbox"] = bbox
    if has_geometry or has_bbox:
        return "RANGE", "UNVERIFIED", "空间范围尚未按当前项目验证"
    if route_status == "VALID":
        return "RANGE", "UNVERIFIED", "路线桩号范围尚未按当前项目验证"
    if route_status == "INVALID":
        return "NONE", "INVALID", route_reason
    if object_type:
        return "NONE", "INVALID", "objectType 必须与 objectId 或空间范围同时提供"
    return "NONE", "VALID", "来源未绑定地图，仅作为参考资料"


def _object_type(value: Any) -> str:
    raw = _upper(value).replace("-", "_").replace(" ", "_")
    aliases = {
        "ROUTE": "ROAD_ROUTE",
        "ROADROUTE": "ROAD_ROUTE",
        "SECTION": "ROAD_SECTION",
        "SEGMENT": "ROAD_SECTION",
        "ROADSECTION": "ROAD_SECTION",
        "ROADSEGMENT": "ROAD_SECTION",
        "ROAD_SEGMENT": "ROAD_SECTION",
        "EVALUATIONUNIT": "EVALUATION_UNIT",
        "EVAL_UNIT": "EVALUATION_UNIT",
        "DISEASE_RECORD": "DISEASE",
        "ASSESSMENT": "ASSESSMENT_RESULT",
        "ASSESSMENT_RESULT_RECORD": "ASSESSMENT_RESULT",
        "REGION": "MAP_REGION",
        "MAPREGION": "MAP_REGION",
    }
    return aliases.get(raw, raw)


def _first_present(data: Dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in data and data[key] is not None:
            return data[key]
    return None


def _text(value: Any) -> Any:
    return None if value in (None, "") else str(value)


def _finite_number(value: Any) -> Tuple[bool, Any]:
    if isinstance(value, bool):
        return False, None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False, None
    if not math.isfinite(number):
        return False, None
    if isinstance(value, int):
        return True, value
    return True, number


def _validate_geometry(value: Any) -> Tuple[bool, Any, str]:
    if not isinstance(value, dict):
        return False, None, "geometry 必须是对象"
    geometry_type = value.get("type")
    if not isinstance(geometry_type, str) or not geometry_type.strip():
        return False, None, "geometry.type 必须是非空字符串"
    geometry_type = geometry_type.strip()
    if geometry_type not in VALID_GEOMETRY_TYPES:
        return False, None, "geometry.type 不是支持的 GeoJSON 类型"

    geometry = copy.deepcopy(value)
    geometry["type"] = geometry_type
    if geometry_type == "GeometryCollection":
        geometries = value.get("geometries")
        if not isinstance(geometries, list) or not geometries:
            return False, None, "geometry.geometries 必须是非空列表"
        normalized_geometries = []
        for item in geometries:
            valid, normalized, reason = _validate_geometry(item)
            if not valid:
                return (
                    False,
                    None,
                    "geometry.geometries 包含无效 geometry: " + reason,
                )
            normalized_geometries.append(normalized)
        geometry["geometries"] = normalized_geometries
        return True, geometry, ""

    valid, coordinates = _validate_geometry_coordinates(
        geometry_type, value.get("coordinates")
    )
    if not valid:
        return False, None, "geometry.coordinates 结构必须非空且仅包含有限数值"
    geometry["coordinates"] = coordinates
    return True, geometry, ""


def _validate_geometry_coordinates(
    geometry_type: str, value: Any
) -> Tuple[bool, Any]:
    if geometry_type == "Point":
        return _validate_position(value)
    if geometry_type == "MultiPoint":
        return _validate_position_sequence(value, minimum=1)
    if geometry_type == "LineString":
        return _validate_position_sequence(value, minimum=2)
    if geometry_type == "MultiLineString":
        return _validate_nested_sequences(value, _validate_line_string)
    if geometry_type == "Polygon":
        return _validate_polygon(value)
    if geometry_type == "MultiPolygon":
        return _validate_nested_sequences(value, _validate_polygon)
    return False, None


def _validate_position(value: Any) -> Tuple[bool, Any]:
    if not isinstance(value, list) or len(value) < 2:
        return False, None
    normalized = []
    for coordinate in value:
        valid, number = _finite_coordinate(coordinate)
        if not valid:
            return False, None
        normalized.append(number)
    return True, normalized


def _validate_position_sequence(
    value: Any, minimum: int
) -> Tuple[bool, Any]:
    if not isinstance(value, list) or len(value) < minimum:
        return False, None
    normalized = []
    for position in value:
        valid, coordinates = _validate_position(position)
        if not valid:
            return False, None
        normalized.append(coordinates)
    return True, normalized


def _validate_line_string(value: Any) -> Tuple[bool, Any]:
    return _validate_position_sequence(value, minimum=2)


def _validate_linear_ring(value: Any) -> Tuple[bool, Any]:
    valid, positions = _validate_position_sequence(value, minimum=4)
    if not valid or positions[0] != positions[-1]:
        return False, None
    return True, positions


def _validate_polygon(value: Any) -> Tuple[bool, Any]:
    return _validate_nested_sequences(value, _validate_linear_ring)


def _validate_nested_sequences(value: Any, validator: Any) -> Tuple[bool, Any]:
    if not isinstance(value, list) or not value:
        return False, None
    normalized = []
    for item in value:
        valid, coordinates = validator(item)
        if not valid:
            return False, None
        normalized.append(coordinates)
    return True, normalized


def _validate_bbox(value: Any) -> Tuple[bool, Any, str]:
    if not isinstance(value, (list, tuple)) or len(value) != 4:
        return False, None, "bbox 必须是包含四个有限数值的列表"
    normalized = []
    for coordinate in value:
        valid, number = _finite_coordinate(coordinate)
        if not valid:
            return False, None, "bbox 必须是包含四个有限数值的列表"
        normalized.append(number)
    min_lng, min_lat, max_lng, max_lat = normalized
    if min_lng > max_lng or min_lat > max_lat:
        return False, None, "bbox 最小坐标不能大于最大坐标"
    return True, normalized, ""


def _normalize_auxiliary_route_range(target: Dict[str, Any]) -> Tuple[str, str]:
    keys = ("routeCode", "startStake", "endStake")
    present = [key in target for key in keys]
    if not any(present):
        return "ABSENT", ""
    if not all(present):
        _remove_route_range(target)
        return (
            "INVALID",
            "路线范围必须同时提供 routeCode、startStake 和 endStake",
        )

    route_code = target.get("routeCode")
    if not isinstance(route_code, str) or not route_code.strip():
        _remove_route_range(target)
        return "INVALID", "routeCode 必须是非空字符串"
    start_valid, start_stake = _finite_number(target.get("startStake"))
    if not start_valid:
        _remove_route_range(target)
        return "INVALID", "startStake 必须是有限数值"
    end_valid, end_stake = _finite_number(target.get("endStake"))
    if not end_valid:
        _remove_route_range(target)
        return "INVALID", "endStake 必须是有限数值"
    if start_stake > end_stake:
        _remove_route_range(target)
        return "INVALID", "startStake 不能大于 endStake"

    target["routeCode"] = route_code.strip()
    target["startStake"] = start_stake
    target["endStake"] = end_stake
    return "VALID", ""


def _remove_route_range(target: Dict[str, Any]) -> None:
    for key in ("routeCode", "startStake", "endStake"):
        target.pop(key, None)


def _normalize_object_auxiliary_spatial_range(target: Dict[str, Any]) -> None:
    if "geometry" in target:
        valid, geometry, _ = _validate_geometry(target.get("geometry"))
        if valid:
            target["geometry"] = geometry
        else:
            target.pop("geometry", None)
    if "bbox" in target:
        valid, bbox, _ = _validate_bbox(target.get("bbox"))
        if valid:
            target["bbox"] = bbox
        else:
            target.pop("bbox", None)


def _finite_coordinate(value: Any) -> Tuple[bool, Any]:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return False, None
    if not math.isfinite(value):
        return False, None
    return True, value


def _sanitize_non_finite_binding_values(result: Dict[str, Any]) -> None:
    for key in ("startStake", "endStake", "start_stake", "end_stake"):
        value = result.get(key)
        if isinstance(value, float) and not math.isfinite(value):
            result[key] = None
    for key in ("geometry", "bbox"):
        if key in result:
            result[key] = _replace_non_finite_numbers(result[key])


def _replace_non_finite_numbers(value: Any) -> Any:
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if isinstance(value, dict):
        return {key: _replace_non_finite_numbers(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_replace_non_finite_numbers(item) for item in value]
    if isinstance(value, tuple):
        return [_replace_non_finite_numbers(item) for item in value]
    return value


def _upper(value: Any) -> str:
    return str(value or "").strip().upper()


def _compact(data: Dict[str, Any]) -> Dict[str, Any]:
    return {
        key: value
        for key, value in data.items()
        if value not in (None, "", [], {})
    }
