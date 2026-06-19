import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple


BUSINESS_TOOL_NAMES = {
    "gis.queryRegionSummary",
    "gis.queryDiseases",
    "gis.queryAssessmentResults",
    "gis.queryDiseasesByStakeRange",
    "gis.queryNearbyObjects",
}

CONFUSING_MARKERS = (
    "未返回 answerMeta",
    "no embedded chunks",
    "大模型未返回有效内容",
    "本次未使用大模型",
    "变量缺失",
)

VALID_BINDING_TYPES = {"OBJECT", "RANGE", "NONE"}
VALID_BINDING_STATUSES = {"VALID", "NOT_FOUND", "INVALID", "UNVERIFIED"}


@dataclass
class AcceptanceCase:
    case_id: str
    name: str
    payload: Dict[str, Any]
    expected_capability: Optional[str] = None
    required_tools: List[str] = field(default_factory=list)
    prohibited_tools: List[str] = field(default_factory=list)
    generation: bool = False
    expected_template: Optional[str] = None
    require_llm: bool = False
    require_locatable_business_sources: bool = False
    followup_replay: bool = False
    expected_followup_map_target: Optional[Dict[str, Any]] = None


FOLLOWUP_REPLAY_CASE_ID = "followup.source_replay"
FOLLOWUP_PARENT_CASE_PRIORITY = (
    "map.disease_analysis",
    "map.assessment_analysis",
    "map.section_analysis",
    "map.route_analysis",
    "map.region_analysis",
    "solution.disease_review",
    "solution.assessment_advice",
    "solution.section_plan",
    "solution.route_report",
    "solution.region_advice",
)


@dataclass
class CaseRunResult:
    case: AcceptanceCase
    response: Dict[str, Any]
    issues: List[str]
    elapsed_ms: int

    @property
    def passed(self) -> bool:
        return not self.issues


def build_acceptance_cases(samples: Dict[str, Any], require_ai: bool = True, top_k: int = 5) -> List[AcceptanceCase]:
    project_id = samples.get("projectId") or samples.get("project_id")
    route = samples["route"]
    section = samples["section"]
    disease = samples["disease"]
    assessment = samples["assessment"]
    geometry = samples["geometry"]
    year = int(first_present(assessment.get("year"), samples.get("year"), 2026))
    route_code = str(route.get("routeCode") or route.get("route_code") or section.get("routeCode") or "")
    common_options = {"useBusinessData": True, "useKnowledge": True, "topK": top_k, "requireAi": require_ai}

    return [
        AcceptanceCase(
            case_id="knowledge.metric_explain",
            name="Explain PCI metric",
            payload=agent_payload(
                action="CHAT",
                message="解释 PCI 指标",
                mode="ROUTE",
                route_code=route_code,
                project_id=project_id,
                year=year,
                options=common_options,
            ),
            expected_capability="knowledge.metric_explain",
            required_tools=["knowledge.retrieve"],
            prohibited_tools=sorted(BUSINESS_TOOL_NAMES),
        ),
        AcceptanceCase(
            case_id="map.route_analysis",
            name="Analyze route",
            payload=agent_payload(
                action="ANALYZE_ROUTE",
                message="分析当前路线",
                mode="ROUTE",
                route_code=route_code,
                project_id=project_id,
                year=year,
                options=common_options,
            ),
            expected_capability="map.route_analysis",
            required_tools=["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "knowledge.retrieve"],
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="map.section_analysis",
            name="Analyze road section",
            payload=agent_payload(
                action="ANALYZE_OBJECT",
                message="分析当前路段",
                mode="OBJECT",
                route_code=section.get("routeCode"),
                project_id=project_id,
                year=year,
                map_object=section,
                options=common_options,
            ),
            expected_capability="map.section_analysis",
            required_tools=["gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "knowledge.retrieve"],
            prohibited_tools=["gis.queryRegionSummary"],
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="map.disease_analysis",
            name="Analyze disease",
            payload=agent_payload(
                action="ANALYZE_OBJECT",
                message="分析当前病害",
                mode="OBJECT",
                route_code=disease.get("routeCode"),
                project_id=project_id,
                year=year,
                map_object=disease,
                options=common_options,
            ),
            expected_capability="map.disease_analysis",
            required_tools=["gis.queryNearbyObjects", "knowledge.retrieve"],
            prohibited_tools=["gis.queryRegionSummary"],
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="map.assessment_analysis",
            name="Analyze assessment result",
            payload=agent_payload(
                action="ANALYZE_OBJECT",
                message="分析当前评定结果",
                mode="OBJECT",
                route_code=assessment.get("routeCode"),
                project_id=project_id,
                year=year,
                map_object=assessment,
                options=common_options,
            ),
            expected_capability="map.assessment_analysis",
            required_tools=["gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "knowledge.retrieve"],
            prohibited_tools=["gis.queryRegionSummary"],
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="map.region_analysis",
            name="Analyze selected region",
            payload=agent_payload(
                action="ANALYZE_REGION",
                message="分析当前区域",
                mode="REGION",
                route_code=route_code,
                project_id=project_id,
                year=year,
                geometry=geometry,
                options=common_options,
            ),
            expected_capability="map.region_analysis",
            required_tools=["gis.queryRegionSummary", "knowledge.retrieve"],
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="solution.route_report",
            name="Generate route report",
            payload=solution_payload(
                action="GENERATE_ROUTE_REPORT",
                message="生成当前路线养护报告",
                mode="ROUTE",
                route_code=route_code,
                project_id=project_id,
                year=year,
                solution_type="ROUTE_REPORT",
                options=common_options,
            ),
            expected_capability="solution.route_report",
            required_tools=["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "knowledge.retrieve", "solution.generateDraft"],
            generation=True,
            expected_template="route_report_default",
            require_llm=require_ai,
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="solution.section_plan",
            name="Generate section maintenance plan",
            payload=solution_payload(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前路段养护计划",
                mode="OBJECT",
                route_code=section.get("routeCode"),
                project_id=project_id,
                year=year,
                map_object=section,
                solution_type="SECTION_PLAN",
                options=common_options,
            ),
            expected_capability="solution.section_plan",
            required_tools=["gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "knowledge.retrieve", "solution.generateDraft"],
            generation=True,
            expected_template="map_object_section_plan_default",
            require_llm=require_ai,
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="solution.disease_review",
            name="Generate disease review",
            payload=solution_payload(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前病害复核意见",
                mode="OBJECT",
                route_code=disease.get("routeCode"),
                project_id=project_id,
                year=year,
                map_object=disease,
                solution_type="DISEASE_REVIEW",
                options=common_options,
            ),
            expected_capability="solution.disease_review",
            required_tools=["gis.queryNearbyObjects", "knowledge.retrieve", "solution.generateDraft"],
            generation=True,
            expected_template="map_object_disease_review_default",
            require_llm=require_ai,
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="solution.assessment_advice",
            name="Generate assessment advice",
            payload=solution_payload(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前评定结果处置建议",
                mode="OBJECT",
                route_code=assessment.get("routeCode"),
                project_id=project_id,
                year=year,
                map_object=assessment,
                solution_type="EVALUATION_UNIT_ADVICE",
                options=common_options,
            ),
            expected_capability="solution.assessment_advice",
            required_tools=["gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "knowledge.retrieve", "solution.generateDraft"],
            generation=True,
            expected_template="map_object_evaluation_unit_advice_default",
            require_llm=require_ai,
            require_locatable_business_sources=True,
        ),
        AcceptanceCase(
            case_id="solution.region_advice",
            name="Generate region advice",
            payload=solution_payload(
                action="GENERATE_REGION_SOLUTION",
                message="生成当前区域养护建议",
                mode="REGION",
                route_code=route_code,
                project_id=project_id,
                year=year,
                geometry=geometry,
                solution_type="REGION_MAINTENANCE_SUGGESTION",
                options=common_options,
            ),
            expected_capability="solution.region_advice",
            required_tools=["gis.queryRegionSummary", "knowledge.retrieve", "solution.generateDraft"],
            generation=True,
            expected_template="map_region_maintenance_advice_default",
            require_llm=require_ai,
            require_locatable_business_sources=True,
        ),
    ]


def validate_case_response(case: AcceptanceCase, response: Dict[str, Any]) -> List[str]:
    data = unwrap_api_response(response)
    issues: List[str] = []
    answer = str(data.get("answer") or "")
    answer_meta = first_dict(data, "answerMeta", "answer_meta")
    tool_results = extract_tool_results(data)
    tool_names = [str(item.get("toolName") or item.get("tool") or item.get("name") or "") for item in tool_results]
    capability_id = capability_from(data, answer_meta)

    if not answer.strip():
        issues.append("empty answer")
    if not answer_meta:
        issues.append("missing answerMeta")
    if case.expected_capability and capability_id != case.expected_capability:
        issues.append(f"capability mismatch: expected {case.expected_capability}, got {capability_id or '-'}")

    for tool_name in case.required_tools:
        if tool_name not in tool_names:
            issues.append(f"missing required tool: {tool_name}")
        else:
            failed = [item for item in tool_results if tool_result_name(item) == tool_name and item.get("success") is False]
            if failed:
                message = failed[0].get("errorMessage") or failed[0].get("error") or failed[0].get("summary") or "unknown error"
                issues.append(f"required tool failed: {tool_name}: {message}")

    for tool_name in case.prohibited_tools:
        if tool_name in tool_names:
            issues.append(f"prohibited tool executed: {tool_name}")

    for item in tool_results:
        if item.get("success") is False:
            name = tool_result_name(item)
            message = item.get("errorMessage") or item.get("error") or item.get("summary") or "unknown error"
            issues.append(f"tool failed: {name}: {message}")

    for marker in CONFUSING_MARKERS:
        if marker in answer:
            issues.append(f"confusing marker in answer: {marker}")

    sources = extract_response_sources(data)
    for source in sources:
        if not binding_contract_ok(source):
            issues.append(f"source binding contract invalid: {source_title(source)}")
        source_type = str(source.get("sourceType") or "").upper()
        metadata = source.get("metadata")
        if source_type in {"KNOWLEDGE", "OUTLINE"} and not metadata and source.get("bindingType") != "NONE":
            issues.append(f"reference source has implicit map binding: {source_title(source)}")

    if case.require_locatable_business_sources:
        business_sources = [source for source in sources if is_business_source(source)]
        if not business_sources:
            issues.append("missing business sources")
        for source in business_sources:
            if not is_locatable_source(source):
                issues.append(f"business source is not locatable: {source_title(source)}")

    if case.followup_replay:
        issues.extend(validate_followup_replay_response(case, data))

    if case.generation:
        issues.extend(validate_generation_response(case, data, tool_results, answer_meta))

    return dedupe_preserve_order(issues)


def validate_followup_replay_response(case: AcceptanceCase, data: Dict[str, Any]) -> List[str]:
    issues: List[str] = []
    map_context = first_dict(data, "mapContext", "map_context") or first_dict(first_dict(data, "data"), "mapContext", "map_context")
    followup_source = extract_followup_source(map_context)
    if not followup_source:
        return ["missing echoed followupSource"]
    if not binding_contract_ok(followup_source):
        issues.append("echoed followupSource binding contract invalid")
        return issues

    binding_type = str(followup_source.get("bindingType") or "").upper()
    if binding_type in {"OBJECT", "RANGE"} and not is_locatable_source(followup_source):
        issues.append("echoed followupSource is not locatable")
    expected_target = case.expected_followup_map_target or {}
    actual_target = source_map_target(followup_source)
    if expected_target and not map_targets_match(expected_target, actual_target):
        issues.append("echoed followupSource mapTarget mismatch")

    planned_tool_names = extract_planned_tool_names(data)
    actual_tool_names = extract_actual_tool_names(data)
    if binding_type == "NONE":
        for tool_name in planned_tool_names:
            if tool_name in BUSINESS_TOOL_NAMES:
                issues.append(f"plain reference follow-up planned GIS tool: {tool_name}")
            elif tool_name != "knowledge.retrieve":
                issues.append(f"plain reference follow-up planned unexpected tool: {tool_name}")
        for tool_name in actual_tool_names:
            if tool_name in BUSINESS_TOOL_NAMES:
                issues.append(f"plain reference follow-up executed GIS tool: {tool_name}")
            elif tool_name != "knowledge.retrieve":
                issues.append(f"plain reference follow-up executed unexpected tool: {tool_name}")
        if "knowledge.retrieve" not in planned_tool_names:
            issues.append("plain reference follow-up missing planned knowledge.retrieve")
        if "knowledge.retrieve" not in actual_tool_names:
            issues.append("plain reference follow-up missing actual knowledge.retrieve")

    target_type = str(actual_target.get("objectType") or actual_target.get("object_type") or "").upper()
    if binding_type == "OBJECT" and target_type in {"DISEASE", "DISEASE_RECORD"}:
        if "gis.queryNearbyObjects" not in planned_tool_names:
            issues.append("disease source follow-up missing planned gis.queryNearbyObjects")
        if "gis.queryNearbyObjects" not in actual_tool_names:
            issues.append("disease source follow-up missing actual gis.queryNearbyObjects")
    return issues


def validate_generation_response(
    case: AcceptanceCase,
    data: Dict[str, Any],
    tool_results: Sequence[Dict[str, Any]],
    answer_meta: Dict[str, Any],
) -> List[str]:
    issues: List[str] = []
    draft_data = generation_data(data, tool_results)
    text = "\n".join(str(value or "") for value in generation_texts(data, draft_data))
    if not draft_data:
        issues.append("missing solution.generateDraft data")
        return issues

    fallback = first_present(
        draft_data.get("fallback"),
        first_dict(draft_data, "templateMeta", "template_meta").get("fallback"),
        first_dict(draft_data, "qualityCheck", "quality_check").get("fallback"),
    )
    if fallback is True or str(fallback).lower() == "true":
        issues.append("generation used fallback template")

    missing_variables = first_list(
        draft_data,
        "missingVariables",
        "missing_variables",
        nested=("templateMeta", "template_meta", "qualityCheck", "quality_check"),
    )
    if missing_variables:
        issues.append("generation missing variables: " + ", ".join(str(item) for item in missing_variables))

    if re.search(r"\{\{[^}]+\}\}", text):
        issues.append("unrendered template variable found")

    if case.expected_template:
        actual_template = first_present(
            draft_data.get("templateCode"),
            draft_data.get("template_code"),
            first_dict(draft_data, "templateMeta", "template_meta").get("templateCode"),
            first_dict(draft_data, "templateMeta", "template_meta").get("template_code"),
            first_dict(data, "actionResult", "action_result").get("templateMeta", {}).get("templateCode")
            if isinstance(first_dict(data, "actionResult", "action_result").get("templateMeta"), dict)
            else None,
        )
        if actual_template and actual_template != case.expected_template:
            issues.append(f"template mismatch: expected {case.expected_template}, got {actual_template}")

    if case.require_llm:
        llm_status = str(first_present(answer_meta.get("llmStatus"), first_dict(draft_data, "answerMeta", "answer_meta").get("llmStatus")) or "")
        if llm_status.upper() in {"FAILED", "SKIPPED", "EMPTY"}:
            issues.append(f"LLM status is {llm_status}")

    return issues


def build_followup_replay_case(parent: CaseRunResult, require_ai: bool = True, top_k: int = 5) -> Optional[AcceptanceCase]:
    source = first_followup_replay_source(parent)
    if not source:
        return None
    followup_context = source_followup_context(source)
    if not binding_contract_ok(followup_context):
        return None
    target = source_map_target(followup_context)
    binding_type = str(followup_context.get("bindingType") or "").upper()

    payload = json.loads(json.dumps(parent.case.payload))
    source_name = source_title(source)
    if binding_type == "NONE":
        message = f"围绕参考资料「{source_name}」继续解释，不要推断不存在的地图对象。"
    else:
        message = f"围绕参考来源「{source_name}」继续分析，说明它与当前地图对象或区域的关系。"
    payload["action"] = "CHAT"
    payload["message"] = message
    payload.setdefault("options", {})
    payload["options"].update({"useBusinessData": True, "useKnowledge": True, "topK": top_k, "requireAi": require_ai})
    payload.pop("actionInput", None)

    map_ctx = payload.setdefault("mapContext", {})
    if not isinstance(map_ctx, dict):
        map_ctx = {}
        payload["mapContext"] = map_ctx
    map_ctx["userQuestion"] = message
    map_ctx["followupSource"] = followup_context
    extra = map_ctx.setdefault("extra", {})
    if not isinstance(extra, dict):
        extra = {}
        map_ctx["extra"] = extra
    extra["followupSource"] = followup_context
    raw_context = extra.setdefault("rawContext", {})
    if not isinstance(raw_context, dict):
        raw_context = {}
        extra["rawContext"] = raw_context
    raw_context["followupSource"] = followup_context

    required_tools = ["knowledge.retrieve"]
    prohibited_tools: List[str] = []
    target_type = str(target.get("objectType") or target.get("object_type") or "").upper()
    if binding_type == "OBJECT" and target_type in {"DISEASE", "DISEASE_RECORD"}:
        required_tools.insert(0, "gis.queryNearbyObjects")
    if binding_type == "NONE":
        prohibited_tools = sorted(BUSINESS_TOOL_NAMES)

    return AcceptanceCase(
        case_id=FOLLOWUP_REPLAY_CASE_ID,
        name="Replay source follow-up",
        payload=payload,
        required_tools=required_tools,
        prohibited_tools=prohibited_tools,
        followup_replay=True,
        expected_followup_map_target=target or None,
    )


def agent_payload(
    *,
    action: str,
    message: str,
    mode: str,
    route_code: Optional[str],
    project_id: Optional[str],
    year: int,
    options: Dict[str, Any],
    map_object: Optional[Dict[str, Any]] = None,
    geometry: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    context = map_context(mode=mode, route_code=route_code, project_id=project_id, year=year, map_object=map_object, geometry=geometry)
    return {"action": action, "message": message, "mapContext": context, "options": dict(options)}


def solution_payload(
    *,
    action: str,
    message: str,
    mode: str,
    route_code: Optional[str],
    project_id: Optional[str],
    year: int,
    solution_type: str,
    options: Dict[str, Any],
    map_object: Optional[Dict[str, Any]] = None,
    geometry: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    context = map_context(mode=mode, route_code=route_code, project_id=project_id, year=year, map_object=map_object, geometry=geometry)
    action_input = {
        "routeCode": route_code,
        "year": year,
        "solutionType": solution_type,
        "options": dict(options),
    }
    if map_object:
        action_input.update(
            {
                "objectType": map_object.get("objectType"),
                "objectId": map_object.get("objectId") or map_object.get("id"),
                "mapObject": map_object,
            }
        )
    if geometry:
        action_input.update({"geometry": geometry, "query": context["extra"]["rawContext"]["query"]})
    return {"action": action, "message": message, "mapContext": context, "actionInput": compact(action_input), "options": dict(options)}


def map_context(
    *,
    mode: str,
    route_code: Optional[str],
    project_id: Optional[str],
    year: int,
    map_object: Optional[Dict[str, Any]] = None,
    geometry: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    query = {"projectId": project_id, "indexCode": "MQI", "grade": "", "sectionTier": "LINE"}
    context = {
        "mode": mode,
        "routeCode": route_code,
        "year": year,
        "mapObject": map_object,
        "region": None,
        "regionSummary": None,
        "regionGeometry": geometry,
        "geometry": geometry,
        "selectedLayers": ["ROAD_ROUTE", "ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
        "analysisTargets": analysis_targets(map_object, route_code),
        "nearbyObjects": [],
        "userQuestion": "",
        "extra": {
            "rawContext": {
                "query": compact(query),
                "selected": (map_object or {}).get("raw") or map_object,
                "mapObject": map_object,
                "selectedMapObject": map_object,
                "regionGeometry": geometry,
                "selectedLayers": ["ROAD_ROUTE", "ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
                "contextScope": mode,
                "indexCode": "MQI",
                "hasRegion": bool(geometry),
            },
            "contextScope": mode,
        },
        "contextScope": mode,
    }
    return compact(context)


def analysis_targets(map_object: Optional[Dict[str, Any]], route_code: Optional[str]) -> List[Dict[str, Any]]:
    if not map_object:
        return []
    return [
        {
            "type": map_object.get("objectType") or map_object.get("object_type"),
            "label": map_object.get("objectType") or "对象",
            "routeCode": map_object.get("routeCode") or route_code,
            "raw": map_object,
        }
    ]


class SrmpHttpClient:
    def __init__(self, base_url: str, tenant_id: str = "default", timeout: int = 120) -> None:
        self.base_url = base_url.rstrip("/")
        self.tenant_id = tenant_id
        self.timeout = timeout

    def get(self, path: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        query = ""
        if params:
            query = "?" + urllib.parse.urlencode(compact(params))
        request = urllib.request.Request(
            self.base_url + path + query,
            headers={"X-Tenant-Id": self.tenant_id},
        )
        return self._open(request)

    def post(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        request = urllib.request.Request(
            self.base_url + path,
            data=json.dumps(payload, ensure_ascii=True).encode("utf-8"),
            headers={"Content-Type": "application/json", "X-Tenant-Id": self.tenant_id},
        )
        return self._open(request)

    def _open(self, request: urllib.request.Request) -> Dict[str, Any]:
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code}: {body[:500]}") from exc


def discover_samples(client: SrmpHttpClient, project_id: Optional[str] = None) -> Dict[str, Any]:
    selected_project_id = project_id or discover_project_id(client)
    route_feature = first_feature(client.get("/api/gis/road-routes", {"projectId": selected_project_id}))
    section_feature = first_feature(client.get("/api/gis/road-sections", {"projectId": selected_project_id}))
    assessment_feature = first_feature(client.get("/api/gis/assessment-results", {"projectId": selected_project_id, "indexCode": "MQI", "sectionTier": "LINE"}))
    disease_row = first_tool_item(client, "gis.queryDiseases", {"projectId": selected_project_id, "limit": 3})

    route = object_from_feature(route_feature, "ROAD_ROUTE")
    section = object_from_feature(section_feature, "ROAD_SECTION")
    assessment = object_from_feature(assessment_feature, "ASSESSMENT_RESULT")
    disease = disease_from_tool_item(disease_row)
    geometry = geometry_from_feature(route_feature)

    return {
        "projectId": selected_project_id,
        "year": assessment.get("year") or 2026,
        "route": route,
        "section": section,
        "disease": disease,
        "assessment": assessment,
        "geometry": geometry,
    }


def discover_project_id(client: SrmpHttpClient) -> str:
    body = client.post("/api/data-mgmt/projects/page", {"pageNo": 1, "pageSize": 50})
    records = (((body or {}).get("data") or {}).get("records") or [])
    for item in records:
        summary = item.get("summary") or {}
        if item.get("id") and (summary.get("roadNetworkReady") or summary.get("routeCount", 0) > 0):
            return str(item["id"])
    if records and records[0].get("id"):
        return str(records[0]["id"])
    raise RuntimeError("No data management project found. Pass --project-id explicitly after importing project data.")


def first_feature(body: Dict[str, Any]) -> Dict[str, Any]:
    features = (((body or {}).get("data") or {}).get("features") or [])
    if not features:
        raise RuntimeError(f"No GIS feature returned: {json.dumps(body, ensure_ascii=False)[:300]}")
    return features[0]


def first_tool_item(client: SrmpHttpClient, tool_name: str, args: Dict[str, Any]) -> Dict[str, Any]:
    body = client.post(
        "/api/agent/tools/execute",
        {
            "toolName": tool_name,
            "tenantId": client.tenant_id,
            "traceId": "map-agent-e2e-sample",
            "userQuestion": "sample",
            "mapContext": {},
            "options": {},
            "args": args,
        },
    )
    data = ((body or {}).get("data") or {}).get("data") or {}
    items = data.get("items") or []
    if not items:
        raise RuntimeError(f"No tool sample returned for {tool_name}: {json.dumps(body, ensure_ascii=False)[:300]}")
    return items[0]


def object_from_feature(feature: Dict[str, Any], object_type: str) -> Dict[str, Any]:
    props = dict(feature.get("properties") or {})
    object_id = str(feature.get("id") or props.get("id") or props.get("objectId") or props.get("object_id") or "")
    props.setdefault("objectType", object_type)
    props.setdefault("objectId", object_id)
    props.setdefault("id", object_id)
    props.setdefault("raw", dict(props))
    return props


def disease_from_tool_item(row: Dict[str, Any]) -> Dict[str, Any]:
    disease = {
        "objectType": "DISEASE",
        "objectId": row.get("id"),
        "id": row.get("id"),
        "routeCode": row.get("route_code") or row.get("routeCode"),
        "direction": row.get("direction"),
        "startStake": row.get("start_stake") or row.get("startStake"),
        "endStake": row.get("end_stake") or row.get("endStake"),
        "diseaseName": row.get("disease_name") or row.get("diseaseName"),
        "diseaseType": row.get("disease_type") or row.get("diseaseType"),
        "severity": row.get("severity"),
        "quantity": row.get("quantity"),
        "unit": row.get("measure_unit") or row.get("unit"),
    }
    disease["raw"] = dict(row)
    disease["raw"].update({"objectType": "DISEASE", "objectId": disease["objectId"]})
    return compact(disease)


def geometry_from_feature(feature: Dict[str, Any]) -> Dict[str, Any]:
    coords = collect_coordinates((feature.get("geometry") or {}).get("coordinates"))
    if not coords:
        return {"type": "Polygon", "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.2], [112.1, 37.1]]]}
    lngs = [item[0] for item in coords]
    lats = [item[1] for item in coords]
    min_lng, max_lng = min(lngs), max(lngs)
    min_lat, max_lat = min(lats), max(lats)
    pad_lng = max((max_lng - min_lng) * 0.05, 0.005)
    pad_lat = max((max_lat - min_lat) * 0.05, 0.005)
    west, east = min_lng - pad_lng, max_lng + pad_lng
    south, north = min_lat - pad_lat, max_lat + pad_lat
    return {"type": "Polygon", "coordinates": [[[west, south], [east, south], [east, north], [west, north], [west, south]]]}


def collect_coordinates(value: Any) -> List[Tuple[float, float]]:
    if not isinstance(value, list):
        return []
    if len(value) >= 2 and isinstance(value[0], (int, float)) and isinstance(value[1], (int, float)):
        return [(float(value[0]), float(value[1]))]
    result: List[Tuple[float, float]] = []
    for item in value:
        result.extend(collect_coordinates(item))
    return result


def run_live_acceptance(
    client: SrmpHttpClient,
    project_id: Optional[str] = None,
    case_filter: Optional[Iterable[str]] = None,
    require_ai: bool = True,
    include_generation: bool = True,
    include_followup_replay: bool = True,
    fail_fast: bool = False,
    top_k: int = 5,
    on_result: Optional[Any] = None,
) -> List[CaseRunResult]:
    samples = discover_samples(client, project_id=project_id)
    cases = build_acceptance_cases(samples, require_ai=require_ai, top_k=top_k)
    if not include_generation:
        cases = [case for case in cases if not case.generation]
    wanted = set(case_filter or [])
    followup_requested = include_followup_replay and (not wanted or FOLLOWUP_REPLAY_CASE_ID in wanted)
    if wanted:
        cases = [
            case
            for case in cases
            if case.case_id in wanted or (FOLLOWUP_REPLAY_CASE_ID in wanted and case.case_id in FOLLOWUP_PARENT_CASE_PRIORITY)
        ]
    if not cases:
        raise RuntimeError("No acceptance cases selected.")

    results: List[CaseRunResult] = []
    for case in cases:
        result = execute_acceptance_case(client, case)
        results.append(result)
        if on_result:
            on_result(result)
        if result.issues and fail_fast:
            return results

    if followup_requested:
        parent = select_followup_parent_result(results)
        replay_case = build_followup_replay_case(parent, require_ai=require_ai, top_k=top_k) if parent else None
        if replay_case:
            result = execute_acceptance_case(client, replay_case)
        else:
            result = CaseRunResult(
                case=AcceptanceCase(case_id=FOLLOWUP_REPLAY_CASE_ID, name="Replay source follow-up", payload={}, followup_replay=True),
                response={},
                issues=["no locatable business source available for follow-up replay"],
                elapsed_ms=0,
            )
        results.append(result)
        if on_result:
            on_result(result)
    return results


def execute_acceptance_case(client: SrmpHttpClient, case: AcceptanceCase) -> CaseRunResult:
    payload = json.loads(json.dumps(case.payload))
    trace_id = "map-agent-e2e-" + re.sub(r"[^A-Za-z0-9_.-]+", "-", case.case_id)
    payload.setdefault("options", {})["traceId"] = f"{trace_id}-{int(time.time() * 1000)}"
    if "actionInput" in payload:
        payload["actionInput"].setdefault("options", {})["traceId"] = payload["options"]["traceId"]
    start = time.perf_counter()
    response = client.post("/api/agent/map-agent/run", payload)
    elapsed_ms = int((time.perf_counter() - start) * 1000)
    issues = validate_case_response(case, response)
    return CaseRunResult(case=case, response=unwrap_api_response(response), issues=issues, elapsed_ms=elapsed_ms)


def unwrap_api_response(response: Dict[str, Any]) -> Dict[str, Any]:
    if isinstance(response, dict) and "data" in response and ("code" in response or "message" in response):
        data = response.get("data")
        return data if isinstance(data, dict) else {}
    return response if isinstance(response, dict) else {}


def extract_tool_results(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    for key in ("toolResults", "tool_results"):
        value = data.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    meta = first_dict(data, "answerMeta", "answer_meta")
    value = meta.get("toolResults") or meta.get("tool_results")
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    return []


def extract_plan_execution(data: Dict[str, Any]) -> Dict[str, Any]:
    for container in (
        data,
        first_dict(data, "data"),
        first_dict(data, "trace"),
        first_dict(data, "answerMeta", "answer_meta"),
    ):
        value = container.get("planExecution") or container.get("plan_execution")
        if isinstance(value, dict):
            return value
    return {}


def extract_planned_tool_names(data: Dict[str, Any]) -> List[str]:
    plan_execution = extract_plan_execution(data)
    names = plan_execution.get("plannedToolNames") or plan_execution.get("planned_tool_names")
    if isinstance(names, list):
        return dedupe_preserve_order(str(item) for item in names if str(item or "").strip())
    for container in (data, first_dict(data, "data"), first_dict(data, "trace")):
        plan = container.get("toolPlan") or container.get("tool_plan")
        if isinstance(plan, list):
            return dedupe_preserve_order(
                str(item.get("toolName") or item.get("tool_name") or "")
                for item in plan
                if isinstance(item, dict) and str(item.get("toolName") or item.get("tool_name") or "").strip()
            )
    return []


def extract_actual_tool_names(data: Dict[str, Any]) -> List[str]:
    plan_execution = extract_plan_execution(data)
    names = plan_execution.get("actualToolNames") or plan_execution.get("actual_tool_names")
    if isinstance(names, list):
        return dedupe_preserve_order(str(item) for item in names if str(item or "").strip())
    return dedupe_preserve_order(tool_result_name(item) for item in extract_tool_results(data) if tool_result_name(item))


def extract_response_sources(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    for key in ("sources", "knowledgeSources", "knowledge_sources"):
        value = data.get(key)
        if isinstance(value, list):
            sources = [item for item in value if isinstance(item, dict)]
            if sources:
                return sources
    nested = first_dict(data, "data")
    for key in ("sources", "knowledgeSources", "knowledge_sources"):
        value = nested.get(key)
        if isinstance(value, list):
            sources = [item for item in value if isinstance(item, dict)]
            if sources:
                return sources
    return []


def is_business_source(source: Dict[str, Any]) -> bool:
    source_type = str(source.get("sourceType") or source.get("source_type") or source.get("type") or "").upper()
    if source_type in {"BUSINESS", "BUSINESS_DATA", "MAP_REGION"}:
        return True
    tool_name = str(source.get("toolName") or source.get("tool_name") or "").lower()
    return tool_name.startswith("gis.")


def binding_contract_ok(source: Dict[str, Any]) -> bool:
    if not isinstance(source, dict):
        return False
    binding_type = source.get("bindingType")
    binding_status = source.get("bindingStatus")
    if binding_type not in VALID_BINDING_TYPES:
        return False
    if binding_status not in VALID_BINDING_STATUSES:
        return False
    target = source.get("mapTarget")
    if binding_type == "NONE":
        return not target
    return isinstance(target, dict) and bool(target)


def is_locatable_source(source: Dict[str, Any]) -> bool:
    if not binding_contract_ok(source):
        return False
    binding_type = str(source.get("bindingType") or "").upper()
    binding_status = str(source.get("bindingStatus") or "").upper()
    if binding_type not in {"OBJECT", "RANGE"} or binding_status not in {"VALID", "UNVERIFIED"}:
        return False
    target = source_map_target(source)
    if binding_type == "OBJECT":
        return bool(target.get("objectType") and target.get("objectId"))
    return bool(
        target.get("geometry")
        or target.get("bbox")
        or (
            target.get("routeCode")
            and target.get("startStake") is not None
            and target.get("endStake") is not None
        )
    )


def source_title(source: Dict[str, Any]) -> str:
    return str(source.get("sourceTitle") or source.get("source_title") or source.get("title") or source.get("sourceId") or source.get("source_id") or "unknown")


def source_map_target(source: Dict[str, Any]) -> Dict[str, Any]:
    target = source.get("mapTarget") or source.get("map_target")
    return target if isinstance(target, dict) else {}


def source_followup_context(source: Dict[str, Any]) -> Dict[str, Any]:
    existing = source.get("followupContext") or source.get("followup_context")
    return json.loads(json.dumps(existing)) if isinstance(existing, dict) else {}


def extract_followup_source(map_context: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(map_context, dict):
        return {}
    direct = map_context.get("followupSource") or map_context.get("followup_source")
    if isinstance(direct, dict):
        return direct
    extra = first_dict(map_context, "extra", "extra_context")
    nested = extra.get("followupSource") or extra.get("followup_source")
    if isinstance(nested, dict):
        return nested
    raw_context = first_dict(extra, "rawContext", "raw_context")
    raw_nested = raw_context.get("followupSource") or raw_context.get("followup_source")
    return raw_nested if isinstance(raw_nested, dict) else {}


def first_followup_replay_source(result: CaseRunResult) -> Dict[str, Any]:
    sources = extract_response_sources(result.response)
    for source in sources:
        if is_business_source(source) and is_locatable_source(source):
            return source
    for source in sources:
        followup = source_followup_context(source)
        if binding_contract_ok(followup):
            return source
    return {}


def select_followup_parent_result(results: Sequence[CaseRunResult]) -> Optional[CaseRunResult]:
    candidates = [result for result in results if result.passed and first_followup_replay_source(result)]
    for case_id in FOLLOWUP_PARENT_CASE_PRIORITY:
        for result in candidates:
            if result.case.case_id == case_id:
                return result
    return candidates[0] if candidates else None


def map_targets_match(expected: Dict[str, Any], actual: Dict[str, Any]) -> bool:
    if not isinstance(expected, dict) or not isinstance(actual, dict):
        return False
    key_groups = (
        ("objectType", "object_type"),
        ("objectId", "object_id", "id"),
        ("routeCode", "route_code"),
        ("startStake", "start_stake"),
        ("endStake", "end_stake"),
        ("geometry",),
        ("bbox",),
    )
    for group in key_groups:
        expected_value = first_present(*(expected.get(key) for key in group))
        if expected_value in (None, "", [], {}):
            continue
        actual_value = first_present(*(actual.get(key) for key in group))
        if not values_match(expected_value, actual_value):
            return False
    return True


def values_match(expected: Any, actual: Any) -> bool:
    if actual in (None, "", [], {}):
        return False
    if isinstance(expected, (dict, list)) or isinstance(actual, (dict, list)):
        return expected == actual
    try:
        return abs(float(expected) - float(actual)) < 0.000001
    except (TypeError, ValueError):
        return str(expected) == str(actual)


def generation_data(data: Dict[str, Any], tool_results: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
    for item in tool_results:
        if tool_result_name(item) == "solution.generateDraft":
            tool_data = item.get("data")
            if isinstance(tool_data, dict):
                return tool_data
    action_result = first_dict(data, "actionResult", "action_result")
    if action_result:
        return action_result
    return {}


def generation_texts(data: Dict[str, Any], draft_data: Dict[str, Any]) -> List[str]:
    action_result = first_dict(data, "actionResult", "action_result")
    return [
        data.get("answer"),
        action_result.get("title"),
        action_result.get("markdown"),
        draft_data.get("title"),
        draft_data.get("markdown"),
    ]


def capability_from(data: Dict[str, Any], answer_meta: Dict[str, Any]) -> Optional[str]:
    trace_capability = first_dict(first_dict(data, "trace"), "capability").get("capabilityId")
    return first_present(
        answer_meta.get("capabilityId"),
        data.get("capabilityId"),
        first_dict(data, "data").get("capabilityId"),
        trace_capability,
    )


def tool_result_name(item: Dict[str, Any]) -> str:
    return str(item.get("toolName") or item.get("tool") or item.get("name") or "")


def first_dict(data: Dict[str, Any], *keys: str) -> Dict[str, Any]:
    if not isinstance(data, dict):
        return {}
    for key in keys:
        value = data.get(key)
        if isinstance(value, dict):
            return value
    return {}


def first_list(data: Dict[str, Any], *keys: str, nested: Sequence[str] = ()) -> List[Any]:
    for key in keys:
        value = data.get(key)
        if isinstance(value, list):
            return value
    for nested_key in nested:
        nested_data = data.get(nested_key)
        if isinstance(nested_data, dict):
            for key in keys:
                value = nested_data.get(key)
                if isinstance(value, list):
                    return value
    return []


def first_present(*values: Any) -> Any:
    for value in values:
        if value not in (None, ""):
            return value
    return None


def compact(data: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in data.items() if value not in (None, "", [], {})}


def dedupe_preserve_order(items: Iterable[str]) -> List[str]:
    result: List[str] = []
    seen = set()
    for item in items:
        if item in seen:
            continue
        seen.add(item)
        result.append(item)
    return result


def summarize_result(result: CaseRunResult) -> Dict[str, Any]:
    data = result.response
    answer_meta = first_dict(data, "answerMeta", "answer_meta")
    tool_results = extract_tool_results(data)
    draft = generation_data(data, tool_results)
    sources = extract_response_sources(data)
    return {
        "caseId": result.case.case_id,
        "name": result.case.name,
        "passed": result.passed,
        "elapsedMs": result.elapsed_ms,
        "issues": result.issues,
        "capabilityId": capability_from(data, answer_meta),
        "answerSource": answer_meta.get("answerSource"),
        "llmStatus": answer_meta.get("llmStatus"),
        "toolNames": [tool_result_name(item) for item in tool_results],
        "plannedToolNames": extract_planned_tool_names(data),
        "actualToolNames": extract_actual_tool_names(data),
        "templateCode": draft.get("templateCode") or first_dict(draft, "templateMeta", "template_meta").get("templateCode"),
        "fallback": draft.get("fallback"),
        "missingVariables": draft.get("missingVariables") or [],
        "sourceCount": len(data.get("sources") or []),
        "knowledgeSourceCount": len(data.get("knowledgeSources") or []),
        "locatableBusinessSourceCount": len([source for source in sources if is_business_source(source) and is_locatable_source(source)]),
        "sourceBindings": [
            compact(
                {
                    "sourceType": source.get("sourceType"),
                    "sourceTitle": source_title(source),
                    "bindingType": source.get("bindingType"),
                    "bindingOrigin": source.get("bindingOrigin"),
                    "bindingStatus": source.get("bindingStatus"),
                    "bindingReason": source.get("bindingReason"),
                    "mapTarget": source.get("mapTarget"),
                }
            )
            for source in sources
        ],
    }


def print_report(results: Sequence[CaseRunResult]) -> None:
    for result in results:
        print_case_result(result)
    print_summary(results)


def print_case_result(result: CaseRunResult) -> None:
    summary = summarize_result(result)
    status = "PASS" if result.passed else "FAIL"
    tools = ", ".join(summary["toolNames"]) or "-"
    print(f"[{status}] {summary['caseId']} ({summary['elapsedMs']} ms)", flush=True)
    print(
        f"  capability={summary.get('capabilityId') or '-'} source={summary.get('answerSource') or '-'} llm={summary.get('llmStatus') or '-'}",
        flush=True,
    )
    if summary.get("templateCode"):
        print(f"  template={summary['templateCode']} fallback={summary.get('fallback')}", flush=True)
    print(f"  tools={tools}", flush=True)
    if result.case.followup_replay:
        print(
            "  followup planned={} actual={}".format(
                ", ".join(summary.get("plannedToolNames") or []) or "-",
                ", ".join(summary.get("actualToolNames") or []) or "-",
            ),
            flush=True,
        )
    if result.issues:
        for issue in result.issues:
            print(f"  - {issue}", flush=True)


def print_summary(results: Sequence[CaseRunResult]) -> None:
    total = len(results)
    failed = len([item for item in results if not item.passed])
    print(f"\nSummary: {total - failed}/{total} passed")


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Run SRMP one-map AI assistant live E2E acceptance checks.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="Java backend base URL.")
    parser.add_argument("--tenant-id", default="default")
    parser.add_argument("--project-id", default=None, help="Use a specific data management project id.")
    parser.add_argument("--case", action="append", dest="cases", help="Run only the named case id. May be repeated.")
    parser.add_argument("--no-generation", action="store_true", help="Skip solution generation cases.")
    parser.add_argument("--no-followup-replay", action="store_true", help="Skip the automatic source follow-up replay case.")
    parser.add_argument("--no-require-ai", action="store_true", help="Do not require LLM success for generation cases.")
    parser.add_argument("--fail-fast", action="store_true")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--json", action="store_true", dest="json_output", help="Print machine-readable JSON report.")
    args = parser.parse_args(argv)

    client = SrmpHttpClient(args.base_url, tenant_id=args.tenant_id, timeout=args.timeout)
    results = run_live_acceptance(
        client,
        project_id=args.project_id,
        case_filter=args.cases,
        require_ai=not args.no_require_ai,
        include_generation=not args.no_generation,
        include_followup_replay=not args.no_followup_replay,
        fail_fast=args.fail_fast,
        top_k=args.top_k,
        on_result=None if args.json_output else print_case_result,
    )
    report = [summarize_result(result) for result in results]
    if args.json_output:
        print(json.dumps({"passed": all(item["passed"] for item in report), "cases": report}, ensure_ascii=False, indent=2))
    else:
        print_summary(results)
    return 0 if all(result.passed for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
