from typing import Any, Dict, Iterable, List

from .schemas import ToolResult


def tool_items(tool_results: Iterable[ToolResult], tool_name: str) -> List[Dict[str, Any]]:
    for result in tool_results or []:
        if result.toolName != tool_name or not result.success:
            continue
        return extract_items(result.data)
    return []


def extract_items(data: Any) -> List[Dict[str, Any]]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if not isinstance(data, dict):
        return []
    for key in ("items", "records", "list", "data", "diseases", "assessments", "hits", "sources"):
        value = data.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    nested = data.get("data")
    if isinstance(nested, dict):
        return extract_items(nested)
    return []


def tool_summary(tool_results: Iterable[ToolResult]) -> Dict[str, Any]:
    summary = {"assessmentCount": 0, "diseaseCount": 0, "heavyDiseaseCount": 0, "toolCount": 0}
    disease_types: Dict[str, int] = {}
    severities: Dict[str, int] = {}
    for result in tool_results or []:
        if not result.success:
            continue
        summary["toolCount"] += 1
        if result.toolName in {"gis.queryDiseases", "gis.queryDiseasesByStakeRange"}:
            count = result.count if isinstance(result.count, int) else len(extract_items(result.data))
            summary["diseaseCount"] += count
            merge_counts(disease_types, nested_counts(result.data, "diseaseTypes"))
            merge_counts(severities, nested_counts(result.data, "severities"))
        if result.toolName == "gis.queryAssessmentResults":
            summary["assessmentCount"] += result.count if isinstance(result.count, int) else len(extract_items(result.data))
    summary["diseaseTypes"] = disease_types
    summary["severities"] = severities
    summary["heavyDiseaseCount"] = severities.get("HEAVY", 0) + severities.get("重", 0) + severities.get("严重", 0)
    return summary


def nested_counts(data: Any, key: str) -> Dict[str, int]:
    if not isinstance(data, dict):
        return {}
    nested_summary = data.get("summary")
    if isinstance(nested_summary, dict) and isinstance(nested_summary.get(key), dict):
        return {str(k): int(v) for k, v in nested_summary.get(key).items()}
    value = data.get(key)
    if isinstance(value, dict):
        return {str(k): int(v) for k, v in value.items()}
    return {}


def merge_counts(target: Dict[str, int], source: Dict[str, int]) -> None:
    for key, value in source.items():
        target[key] = target.get(key, 0) + int(value)


def first(data: Dict[str, Any], *keys: str) -> str:
    if not isinstance(data, dict):
        return ""
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first(raw, *keys)
    return ""
