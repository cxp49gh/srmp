import hashlib
import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from .intent import normalize_object_type
from .schemas import MapAiAgentRequest


DEFAULT_DATA_DIR = Path(__file__).resolve().parent / "governance_data"
DEFAULT_TOOL_POLICY = {"required": [], "optional": [], "adaptive": [], "prohibited": []}


@dataclass(frozen=True)
class GovernanceRegistry:
    version: str
    tool_version: str
    capabilities: List[Dict[str, Any]]
    tools: List[Dict[str, Any]]
    capability_by_id: Dict[str, Dict[str, Any]]
    tool_by_name: Dict[str, Dict[str, Any]]
    validation: Dict[str, Any]

    @property
    def config_valid(self) -> bool:
        return not self.validation.get("errors")


@lru_cache(maxsize=1)
def governance_registry() -> GovernanceRegistry:
    capabilities_raw = _load_json(_capabilities_path())
    tools_raw = _load_json(_tools_path())
    return build_governance_registry(capabilities_raw, tools_raw)


def build_governance_registry(capabilities_raw: Dict[str, Any], tools_raw: Dict[str, Any]) -> GovernanceRegistry:
    capabilities = [normalize_capability(item) for item in capabilities_raw.get("capabilities") or []]
    tools = [normalize_tool(item) for item in tools_raw.get("tools") or []]
    registry = GovernanceRegistry(
        version=str(capabilities_raw.get("version") or "unknown"),
        tool_version=str(tools_raw.get("version") or "unknown"),
        capabilities=capabilities,
        tools=tools,
        capability_by_id={str(item.get("id")): item for item in capabilities if item.get("id")},
        tool_by_name={str(item.get("name")): item for item in tools if item.get("name")},
        validation={},
    )
    validation = validate_governance_registry(registry)
    return GovernanceRegistry(
        version=registry.version,
        tool_version=registry.tool_version,
        capabilities=registry.capabilities,
        tools=registry.tools,
        capability_by_id=registry.capability_by_id,
        tool_by_name=registry.tool_by_name,
        validation=validation,
    )


def governance_config_bundle() -> Dict[str, Any]:
    capabilities_path = _capabilities_path()
    tools_path = _tools_path()
    capabilities_raw = _load_json(capabilities_path)
    tools_raw = _load_json(tools_path)
    registry = build_governance_registry(capabilities_raw, tools_raw)
    return _governance_config_payload(
        registry=registry,
        capabilities_raw=capabilities_raw,
        tools_raw=tools_raw,
        mode="ACTIVE",
        config_files={
            "capabilities": _config_path_label(capabilities_path, "capabilities.json"),
            "tools": _config_path_label(tools_path, "tools.json"),
        },
    )


def governance_config_draft_validate(payload: Dict[str, Any]) -> Dict[str, Any]:
    capabilities_raw = dict((payload or {}).get("capabilitiesConfig") or {})
    tools_raw = dict((payload or {}).get("toolsConfig") or {})
    registry = build_governance_registry(capabilities_raw, tools_raw)
    active_capabilities_raw = _load_json(_capabilities_path())
    active_tools_raw = _load_json(_tools_path())
    result = _governance_config_payload(
        registry=registry,
        capabilities_raw=capabilities_raw,
        tools_raw=tools_raw,
        mode="DRAFT_VALIDATE",
        config_files={
            "capabilities": "draft.capabilitiesConfig",
            "tools": "draft.toolsConfig",
        },
    )
    result["draftId"] = _stable_hash({
        "capabilitiesConfig": capabilities_raw,
        "toolsConfig": tools_raw,
    })[:16]
    result["readiness"] = governance_readiness_for_registry(registry)
    result["diff"] = governance_config_diff(
        base_capabilities_raw=active_capabilities_raw,
        base_tools_raw=active_tools_raw,
        draft_capabilities_raw=capabilities_raw,
        draft_tools_raw=tools_raw,
    )
    return result


def resolve_capability(
    request: MapAiAgentRequest,
    fallback_intent: str,
    intent_detail: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    registry = governance_registry()
    candidates: List[Tuple[float, Dict[str, Any], List[str], str]] = []
    for capability in registry.capabilities:
        if not capability.get("enabled", True):
            continue
        score, rules, context_usage = _match_capability(capability, request, fallback_intent, intent_detail or {})
        if score <= 0:
            continue
        candidates.append((score, capability, rules, context_usage))

    if not candidates:
        return fallback_capability(fallback_intent, intent_detail or {})

    candidates.sort(key=lambda item: (item[0], int(item[1].get("priority") or 0)), reverse=True)
    score, capability, rules, context_usage = candidates[0]
    return capability_match(capability, score, rules, context_usage)


def governance_summary() -> Dict[str, Any]:
    registry = governance_registry()
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "configValid": registry.config_valid,
        "validation": registry.validation,
        "capabilityCount": len(registry.capabilities),
        "enabledCapabilityCount": sum(1 for item in registry.capabilities if item.get("enabled", True)),
        "toolCount": len(registry.tools),
        "capabilities": [_capability_summary(item) for item in registry.capabilities],
        "tools": registry.tools,
    }


def validate_governance() -> Dict[str, Any]:
    registry = governance_registry()
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "configValid": registry.config_valid,
        "validation": registry.validation,
    }


def governance_policy_examples() -> List[Dict[str, Any]]:
    registry = governance_registry()
    examples: List[Dict[str, Any]] = []
    for capability in registry.capabilities:
        for example in capability.get("examples") or []:
            item = dict(example)
            item["capabilityId"] = capability.get("id")
            item["capabilityName"] = capability.get("name")
            item["capabilityCategory"] = capability.get("category")
            examples.append(item)
    return examples


def governance_tool_impact() -> Dict[str, Any]:
    registry = governance_registry()
    return governance_tool_impact_for_registry(registry)


def governance_tool_impact_for_registry(registry: GovernanceRegistry) -> Dict[str, Any]:
    tools: List[Dict[str, Any]] = []
    for tool in registry.tools:
        item = dict(tool)
        relations = _tool_capability_relations(str(tool.get("name") or ""), registry.capabilities)
        item.update(relations)
        relation_counts = {
            "required": len(relations["requiredBy"]),
            "optional": len(relations["optionalBy"]),
            "adaptive": len(relations["adaptiveBy"]),
            "prohibited": len(relations["prohibitedBy"]),
        }
        item["relationCounts"] = relation_counts
        item["affectedCapabilityCount"] = len({
            capability.get("id")
            for key in ("requiredBy", "optionalBy", "adaptiveBy")
            for capability in relations[key]
        })
        item["riskLevel"] = _tool_risk_level(item)
        tools.append(item)
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "toolCount": len(tools),
        "tools": tools,
    }


def governance_capability_detail(capability_id: str) -> Optional[Dict[str, Any]]:
    registry = governance_registry()
    capability = registry.capability_by_id.get(str(capability_id or "").strip())
    if not capability:
        return None
    policy = normalize_tool_policy(capability.get("toolPolicy") or {})
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "capability": _capability_summary(capability),
        "toolNames": policy,
        "tools": {
            "required": [_tool_detail(name, registry.tool_by_name) for name in policy.get("required") or []],
            "optional": [_tool_detail(name, registry.tool_by_name) for name in policy.get("optional") or []],
            "adaptive": [_tool_detail(name, registry.tool_by_name) for name in policy.get("adaptive") or []],
            "prohibited": [_tool_detail(name, registry.tool_by_name) for name in policy.get("prohibited") or []],
        },
        "examples": capability.get("examples") or [],
        "developerGuide": _developer_guide(),
    }


def governance_tool_detail(tool_name: str, contract: Optional[Dict[str, Any]] = None) -> Optional[Dict[str, Any]]:
    registry = governance_registry()
    name = str(tool_name or "").strip()
    tool = registry.tool_by_name.get(name)
    if not tool:
        return None
    relations = _tool_capability_relations(name, registry.capabilities)
    relation_counts = {
        "required": len(relations["requiredBy"]),
        "optional": len(relations["optionalBy"]),
        "adaptive": len(relations["adaptiveBy"]),
        "prohibited": len(relations["prohibitedBy"]),
    }
    affected_count = len({
        capability.get("id")
        for key in ("requiredBy", "optionalBy", "adaptiveBy")
        for capability in relations[key]
    })
    item = dict(tool)
    item.update(relations)
    item["affectedCapabilityCount"] = affected_count
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "tool": tool,
        **relations,
        "relationCounts": relation_counts,
        "affectedCapabilityCount": affected_count,
        "riskLevel": _tool_risk_level(item),
        "contract": _tool_contract_status(name, contract),
        "developerGuide": _tool_developer_guide(),
    }


def governance_readiness(
    contract: Optional[Dict[str, Any]] = None,
    policy_coverage: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    return governance_readiness_for_registry(governance_registry(), contract=contract, policy_coverage=policy_coverage)


def governance_readiness_for_registry(
    registry: GovernanceRegistry,
    contract: Optional[Dict[str, Any]] = None,
    policy_coverage: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tool_impact = governance_tool_impact_for_registry(registry)
    issues: List[Dict[str, Any]] = []

    for item in registry.validation.get("errors") or []:
        issues.append(_readiness_issue("ERROR", "CONFIG_ERROR", "CONFIG", str(item.get("message") or item.get("code") or ""), code=str(item.get("code") or "CONFIG_ERROR")))
    for item in registry.validation.get("warnings") or []:
        issues.append(_readiness_issue("WARN", "CONFIG_WARNING", "CONFIG", str(item.get("message") or item.get("code") or ""), code=str(item.get("code") or "CONFIG_WARNING")))

    coverage_failed_count = 0
    if isinstance(policy_coverage, dict):
        failed_cases = [case for case in policy_coverage.get("cases") or [] if case.get("status") != "PASS"]
        coverage_failed_count = len(failed_cases)
        for case in failed_cases:
            issues.append(_readiness_issue(
                "ERROR",
                "POLICY_CASE_FAILED",
                "POLICY",
                "策略样例失败：" + str(case.get("name") or case.get("id") or ""),
                capability_id=str(case.get("expectedCapabilityId") or case.get("actualCapabilityId") or ""),
                case_id=str(case.get("id") or ""),
            ))

    tool_rows = tool_impact.get("tools") or []
    orphan_tools = [tool for tool in tool_rows if int(tool.get("affectedCapabilityCount") or 0) <= 0]
    for tool in orphan_tools:
        issues.append(_readiness_issue(
            "WARN",
            "TOOL_WITHOUT_CAPABILITY",
            "TOOL",
            "工具未被任何能力 required/optional/adaptive 使用：" + str(tool.get("name") or ""),
            tool_name=str(tool.get("name") or ""),
        ))

    if isinstance(contract, dict):
        issues.extend(_contract_readiness_issues(tool_rows, contract))

    severity_counts = _severity_counts(issues)
    status = "FAIL" if severity_counts["ERROR"] else "WARN" if severity_counts["WARN"] else "PASS"
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "status": status,
        "summary": {
            "capabilityCount": len(registry.capabilities),
            "toolCount": len(registry.tools),
            "validationErrorCount": int(registry.validation.get("errorCount") or 0),
            "validationWarningCount": int(registry.validation.get("warningCount") or 0),
            "policyCaseCount": int((policy_coverage or {}).get("caseCount") or 0) if isinstance(policy_coverage, dict) else None,
            "policyFailedCount": coverage_failed_count,
            "orphanToolCount": len(orphan_tools),
            "contractChecked": isinstance(contract, dict),
            "issueCount": len(issues),
            "errorCount": severity_counts["ERROR"],
            "warningCount": severity_counts["WARN"],
        },
        "issues": issues,
        "contract": _contract_readiness_summary(contract),
        "policyCoverage": policy_coverage or {},
    }


def normalize_capability(item: Dict[str, Any]) -> Dict[str, Any]:
    data = dict(item or {})
    data["id"] = str(data.get("id") or "").strip()
    data["name"] = str(data.get("name") or data["id"]).strip()
    data["category"] = str(data.get("category") or "GENERAL").strip().upper()
    data["intent"] = str(data.get("intent") or "GENERAL_CHAT").strip().upper()
    data["enabled"] = bool(data.get("enabled", True))
    data["priority"] = int(data.get("priority") or 0)
    data["legacyIntents"] = [str(value).strip().upper() for value in data.get("legacyIntents") or [] if str(value or "").strip()]
    data["triggers"] = _normalize_trigger_block(data.get("triggers") or {})
    data["contextPolicy"] = dict(data.get("contextPolicy") or {})
    data["toolPolicy"] = normalize_tool_policy(data.get("toolPolicy") or {})
    data["examples"] = [_normalize_policy_example(example, data["id"]) for example in data.get("examples") or [] if isinstance(example, dict)]
    return data


def normalize_tool(item: Dict[str, Any]) -> Dict[str, Any]:
    data = dict(item or {})
    data["name"] = str(data.get("name") or "").strip()
    data["label"] = str(data.get("label") or data["name"]).strip()
    data["category"] = str(data.get("category") or "GENERAL").strip().upper()
    data["readOnly"] = bool(data.get("readOnly", True))
    data["writeRisk"] = bool(data.get("writeRisk", False))
    data["enabled"] = bool(data.get("enabled", True))
    return data


def normalize_tool_policy(value: Dict[str, Any]) -> Dict[str, List[str]]:
    policy: Dict[str, List[str]] = {}
    for key in DEFAULT_TOOL_POLICY:
        policy[key] = _string_list(value.get(key))
    return policy


def capability_match(capability: Dict[str, Any], score: float, rules: List[str], context_usage: str) -> Dict[str, Any]:
    policy = normalize_tool_policy(capability.get("toolPolicy") or {})
    return {
        "capabilityId": capability.get("id"),
        "name": capability.get("name"),
        "category": capability.get("category"),
        "intent": capability.get("intent"),
        "confidence": round(min(0.99, max(0.1, score / 300.0)), 2),
        "matchedRules": rules,
        "contextUsage": context_usage,
        "contextPolicy": capability.get("contextPolicy") or {},
        "toolPolicy": policy,
    }


def fallback_capability(fallback_intent: str, intent_detail: Dict[str, Any]) -> Dict[str, Any]:
    intent = str(fallback_intent or "GENERAL_CHAT").strip().upper()
    return {
        "capabilityId": "legacy." + intent.lower(),
        "name": intent,
        "category": "LEGACY",
        "intent": intent,
        "confidence": 0.2,
        "matchedRules": ["legacyIntent:" + intent],
        "contextUsage": str((intent_detail or {}).get("mode") or "DEFAULT"),
        "contextPolicy": {},
        "toolPolicy": dict(DEFAULT_TOOL_POLICY),
    }


def validate_governance_registry(registry: GovernanceRegistry) -> Dict[str, Any]:
    errors: List[Dict[str, str]] = []
    warnings: List[Dict[str, str]] = []
    seen_ids = set()
    for capability in registry.capabilities:
        capability_id = str(capability.get("id") or "")
        if not capability_id:
            errors.append({"code": "CAPABILITY_ID_MISSING", "message": "能力缺少 id。"})
            continue
        if capability_id in seen_ids:
            errors.append({"code": "CAPABILITY_ID_DUPLICATE", "message": "能力 id 重复：" + capability_id})
        seen_ids.add(capability_id)
        policy = normalize_tool_policy(capability.get("toolPolicy") or {})
        required = set(policy.get("required") or [])
        optional = set(policy.get("optional") or [])
        adaptive = set(policy.get("adaptive") or [])
        prohibited = set(policy.get("prohibited") or [])
        for tool_name in sorted(required | optional | adaptive | prohibited):
            if tool_name not in registry.tool_by_name:
                errors.append({"code": "TOOL_NOT_FOUND", "message": capability_id + " 引用了不存在的工具：" + tool_name})
        conflict = (required | optional | adaptive) & prohibited
        for tool_name in sorted(conflict):
            errors.append({"code": "TOOL_POLICY_CONFLICT", "message": capability_id + " 同时允许和禁止工具：" + tool_name})
        if capability.get("enabled", True) and not (required or optional):
            warnings.append({"code": "CAPABILITY_WITHOUT_TOOLS", "message": capability_id + " 没有 required/optional 工具。"})
        for tool_name in sorted(required | adaptive):
            tool = registry.tool_by_name.get(tool_name) or {}
            if tool.get("writeRisk"):
                errors.append({"code": "WRITE_TOOL_IN_AUTO_POLICY", "message": capability_id + " 自动策略包含写风险工具：" + tool_name})
    return {"errors": errors, "warnings": warnings, "errorCount": len(errors), "warningCount": len(warnings)}


def _governance_config_payload(
    registry: GovernanceRegistry,
    capabilities_raw: Dict[str, Any],
    tools_raw: Dict[str, Any],
    mode: str,
    config_files: Dict[str, str],
) -> Dict[str, Any]:
    return {
        "mode": mode,
        "runtimeMutable": False,
        "publishMode": "restart-required",
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "configValid": registry.config_valid,
        "validation": registry.validation,
        "capabilitiesHash": _stable_hash(capabilities_raw),
        "toolsHash": _stable_hash(tools_raw),
        "configFiles": config_files,
        "capabilitiesConfig": capabilities_raw,
        "toolsConfig": tools_raw,
        "summary": {
            "capabilityCount": len(registry.capabilities),
            "enabledCapabilityCount": sum(1 for item in registry.capabilities if item.get("enabled", True)),
            "toolCount": len(registry.tools),
            "validationErrorCount": int(registry.validation.get("errorCount") or 0),
            "validationWarningCount": int(registry.validation.get("warningCount") or 0),
        },
    }


def governance_config_diff(
    base_capabilities_raw: Dict[str, Any],
    base_tools_raw: Dict[str, Any],
    draft_capabilities_raw: Dict[str, Any],
    draft_tools_raw: Dict[str, Any],
) -> Dict[str, Any]:
    capability_changes = _list_config_changes(
        base_items=base_capabilities_raw.get("capabilities") or [],
        draft_items=draft_capabilities_raw.get("capabilities") or [],
        key="id",
        label_key="name",
        output_key="id",
    )
    tool_changes = _list_config_changes(
        base_items=base_tools_raw.get("tools") or [],
        draft_items=draft_tools_raw.get("tools") or [],
        key="name",
        label_key="label",
        output_key="name",
    )
    root_changed_fields = {
        "capabilities": _changed_fields(
            {k: v for k, v in base_capabilities_raw.items() if k != "capabilities"},
            {k: v for k, v in draft_capabilities_raw.items() if k != "capabilities"},
        ),
        "tools": _changed_fields(
            {k: v for k, v in base_tools_raw.items() if k != "tools"},
            {k: v for k, v in draft_tools_raw.items() if k != "tools"},
        ),
    }
    summary = {
        "capabilityAddedCount": sum(1 for item in capability_changes if item.get("changeType") == "ADDED"),
        "capabilityRemovedCount": sum(1 for item in capability_changes if item.get("changeType") == "REMOVED"),
        "capabilityModifiedCount": sum(1 for item in capability_changes if item.get("changeType") == "MODIFIED"),
        "toolAddedCount": sum(1 for item in tool_changes if item.get("changeType") == "ADDED"),
        "toolRemovedCount": sum(1 for item in tool_changes if item.get("changeType") == "REMOVED"),
        "toolModifiedCount": sum(1 for item in tool_changes if item.get("changeType") == "MODIFIED"),
        "rootChangedCount": len(root_changed_fields["capabilities"]) + len(root_changed_fields["tools"]),
    }
    summary["changeCount"] = sum(int(value or 0) for value in summary.values())
    return {
        "changed": bool(summary["changeCount"]),
        "baseHashes": {
            "capabilities": _stable_hash(base_capabilities_raw),
            "tools": _stable_hash(base_tools_raw),
        },
        "draftHashes": {
            "capabilities": _stable_hash(draft_capabilities_raw),
            "tools": _stable_hash(draft_tools_raw),
        },
        "summary": summary,
        "rootChangedFields": root_changed_fields,
        "capabilities": capability_changes,
        "tools": tool_changes,
    }


def _list_config_changes(
    base_items: List[Dict[str, Any]],
    draft_items: List[Dict[str, Any]],
    key: str,
    label_key: str,
    output_key: str,
) -> List[Dict[str, Any]]:
    base_by_key = _index_config_items(base_items, key)
    draft_by_key = _index_config_items(draft_items, key)
    changes: List[Dict[str, Any]] = []
    for item_key in sorted(set(base_by_key.keys()) | set(draft_by_key.keys())):
        before = base_by_key.get(item_key)
        after = draft_by_key.get(item_key)
        if before is None and after is not None:
            changes.append(_config_change_item(output_key, item_key, "ADDED", [], before, after, label_key))
            continue
        if before is not None and after is None:
            changes.append(_config_change_item(output_key, item_key, "REMOVED", [], before, after, label_key))
            continue
        changed_fields = _changed_fields(before or {}, after or {})
        if changed_fields:
            changes.append(_config_change_item(output_key, item_key, "MODIFIED", changed_fields, before, after, label_key))
    return changes


def _index_config_items(items: List[Dict[str, Any]], key: str) -> Dict[str, Dict[str, Any]]:
    indexed: Dict[str, Dict[str, Any]] = {}
    for item in items:
        if not isinstance(item, dict):
            continue
        item_key = str(item.get(key) or "").strip()
        if item_key:
            indexed[item_key] = item
    return indexed


def _config_change_item(
    output_key: str,
    item_key: str,
    change_type: str,
    changed_fields: List[str],
    before: Optional[Dict[str, Any]],
    after: Optional[Dict[str, Any]],
    label_key: str,
) -> Dict[str, Any]:
    current = after if isinstance(after, dict) else before if isinstance(before, dict) else {}
    item = {
        output_key: item_key,
        "label": str((current or {}).get(label_key) or item_key),
        "changeType": change_type,
        "changedFields": changed_fields,
    }
    if isinstance(before, dict):
        item["before"] = _config_item_preview(before, label_key)
    if isinstance(after, dict):
        item["after"] = _config_item_preview(after, label_key)
    return item


def _config_item_preview(item: Dict[str, Any], label_key: str) -> Dict[str, Any]:
    preview = {
        "label": item.get(label_key),
        "enabled": item.get("enabled"),
    }
    for key in ("category", "intent", "priority", "readOnly", "writeRisk"):
        if key in item:
            preview[key] = item.get(key)
    return preview


def _changed_fields(before: Dict[str, Any], after: Dict[str, Any]) -> List[str]:
    fields = []
    for key in sorted(set((before or {}).keys()) | set((after or {}).keys())):
        if (before or {}).get(key) != (after or {}).get(key):
            fields.append(str(key))
    return fields


def _capabilities_path() -> Path:
    return Path(os.getenv("SRMP_AGENT_CAPABILITIES_PATH") or DEFAULT_DATA_DIR / "capabilities.json")


def _tools_path() -> Path:
    return Path(os.getenv("SRMP_AGENT_TOOLS_PATH") or DEFAULT_DATA_DIR / "tools.json")


def _config_path_label(path: Path, filename: str) -> str:
    default_path = DEFAULT_DATA_DIR / filename
    if path.resolve() == default_path.resolve():
        return "srmp-ai-orchestrator/app/governance_data/" + filename
    return str(path)


def _stable_hash(value: Any) -> str:
    raw = json.dumps(value, sort_keys=True, ensure_ascii=False, default=str)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _match_capability(
    capability: Dict[str, Any],
    request: MapAiAgentRequest,
    fallback_intent: str,
    intent_detail: Dict[str, Any],
) -> Tuple[float, List[str], str]:
    triggers = capability.get("triggers") or {}
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    action = str(request.action or (request.options or {}).get("action") or intent_detail.get("action") or "").strip().upper()
    mode = str(ctx.mode or "" if ctx else "").strip().upper()
    object_type = normalize_object_type(_first_string(obj, "objectType", "object_type", "type", "layerType"))
    message = request.message or ""
    score = float(capability.get("priority") or 0)
    rules: List[str] = []
    concrete_match = False
    context_policy = capability.get("contextPolicy") or {}

    if _should_defer_to_business_scope(context_policy, request, mode, obj, message):
        return 0, [], ""

    actions = triggers.get("actions") or []
    if actions and action in actions:
        score += 220
        concrete_match = True
        rules.append("action:" + action)

    object_types = triggers.get("objectTypes") or []
    if object_types and object_type in object_types:
        score += 160
        concrete_match = True
        rules.append("objectType:" + object_type)

    modes = triggers.get("modes") or []
    if modes and mode in modes:
        score += 80
        concrete_match = True
        rules.append("mode:" + mode)

    include_keywords = triggers.get("includeKeywords") or []
    include_matches = [word for word in include_keywords if word and word in message]
    if include_keywords:
        if not include_matches:
            return 0, [], ""
        concrete_match = True
        score += 80 + len(include_matches) * 5
        rules.extend(["keyword:" + word for word in include_matches[:5]])

    question_keywords = triggers.get("questionKeywords") or []
    question_matches = [word for word in question_keywords if word and word in message]
    if question_keywords:
        if not question_matches:
            return 0, [], ""
        concrete_match = True
        score += 40 + len(question_matches) * 3
        rules.extend(["questionKeyword:" + word for word in question_matches[:5]])

    legacy_intents = capability.get("legacyIntents") or []
    fallback = str(fallback_intent or "").strip().upper()
    if fallback and fallback in legacy_intents:
        score += 25
        rules.append("legacyIntent:" + fallback)
        if not (include_keywords or question_keywords or actions or object_types or modes):
            concrete_match = True

    if not concrete_match:
        return 0, [], ""
    context_usage = str(context_policy.get("contextUsage") or "DEFAULT")
    return score, rules, context_usage


def _should_defer_to_business_scope(
    context_policy: Dict[str, Any],
    request: MapAiAgentRequest,
    mode: str,
    obj: Dict[str, Any],
    message: str,
) -> bool:
    if not context_policy.get("ignoreBusinessScopeByDefault"):
        return False
    explicit_phrases = _string_list(context_policy.get("businessScopeRequiresExplicitPhrase"))
    if explicit_phrases and not any(phrase in message for phrase in explicit_phrases):
        return False
    return _has_business_scope(request, mode, obj)


def _has_business_scope(request: MapAiAgentRequest, mode: str, obj: Dict[str, Any]) -> bool:
    ctx = request.mapContext
    if obj:
        return True
    if not ctx:
        return False
    if ctx.routeCode or ctx.regionSummary or ctx.geometry:
        return True
    return mode in {"OBJECT", "ROUTE", "REGION", "BOX", "POLYGON", "SELECTION"}


def _capability_summary(capability: Dict[str, Any]) -> Dict[str, Any]:
    policy = normalize_tool_policy(capability.get("toolPolicy") or {})
    return {
        "id": capability.get("id"),
        "name": capability.get("name"),
        "category": capability.get("category"),
        "enabled": capability.get("enabled", True),
        "priority": capability.get("priority"),
        "intent": capability.get("intent"),
        "legacyIntents": capability.get("legacyIntents") or [],
        "triggers": capability.get("triggers") or {},
        "contextPolicy": capability.get("contextPolicy") or {},
        "toolPolicy": policy,
    }


def _normalize_trigger_block(value: Dict[str, Any]) -> Dict[str, List[str]]:
    return {
        "actions": [item.upper() for item in _string_list(value.get("actions"))],
        "modes": [item.upper() for item in _string_list(value.get("modes"))],
        "objectTypes": [normalize_object_type(item) for item in _string_list(value.get("objectTypes"))],
        "includeKeywords": _string_list(value.get("includeKeywords")),
        "questionKeywords": _string_list(value.get("questionKeywords")),
    }


def _tool_capability_relations(tool_name: str, capabilities: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
    relations = {"requiredBy": [], "optionalBy": [], "adaptiveBy": [], "prohibitedBy": []}
    mapping = {
        "required": "requiredBy",
        "optional": "optionalBy",
        "adaptive": "adaptiveBy",
        "prohibited": "prohibitedBy",
    }
    for capability in capabilities:
        policy = normalize_tool_policy(capability.get("toolPolicy") or {})
        summary = {
            "id": capability.get("id"),
            "name": capability.get("name"),
            "category": capability.get("category"),
            "enabled": capability.get("enabled", True),
        }
        for policy_key, relation_key in mapping.items():
            if tool_name in policy.get(policy_key, []):
                relations[relation_key].append(summary)
    return relations


def _tool_risk_level(tool: Dict[str, Any]) -> str:
    if tool.get("writeRisk"):
        return "HIGH"
    required_count = len(tool.get("requiredBy") or [])
    affected_count = int(tool.get("affectedCapabilityCount") or 0)
    if required_count >= 3 or affected_count >= 5:
        return "MEDIUM"
    if required_count > 0 or affected_count > 0:
        return "LOW"
    return "NONE"


def _tool_detail(tool_name: str, tool_by_name: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    tool = tool_by_name.get(tool_name)
    if not tool:
        return {"name": tool_name, "label": tool_name, "missing": True}
    data = dict(tool)
    data["missing"] = False
    return data


def _developer_guide() -> Dict[str, Any]:
    return {
        "configFiles": [
            "srmp-ai-orchestrator/app/governance_data/capabilities.json",
            "srmp-ai-orchestrator/app/governance_data/tools.json",
        ],
        "steps": [
            "配置能力触发条件",
            "配置 required/optional/adaptive/prohibited 工具边界",
            "补充策略样例并运行覆盖校验",
            "在 Java AiToolRegistry 注册新工具并保持工具名一致",
            "在 AI 执行过程和能力治理页面验证 answerMeta、工具计划和证据链",
        ],
        "verificationCommands": [
            "PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator/tests/test_governance_api.py -v",
            "npm --prefix srmp-web-ui run build",
        ],
    }


def _tool_contract_status(tool_name: str, contract: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not isinstance(contract, dict):
        return {"checked": False}
    java_tools = set(_string_list(contract.get("javaTools")))
    allowed_tools = set(_string_list(contract.get("runtimeAllowedTools")))
    missing_in_java = set(_string_list(contract.get("missingInJava")))
    blocked_by_runtime = set(_string_list(contract.get("blockedByRuntimeWhitelist")))
    write_blocked = set(_string_list(contract.get("writeBlocked")))
    return {
        "checked": True,
        "gatewayOk": bool(contract.get("ok")),
        "javaRegistered": tool_name in java_tools,
        "runtimeAllowed": (not allowed_tools or tool_name in allowed_tools) and tool_name not in blocked_by_runtime,
        "missingInJava": tool_name in missing_in_java,
        "blockedByRuntimeWhitelist": tool_name in blocked_by_runtime,
        "writeBlocked": tool_name in write_blocked,
        "javaToolCount": len(java_tools),
        "runtimeAllowedToolCount": len(allowed_tools),
    }


def _tool_developer_guide() -> Dict[str, Any]:
    return {
        "configFiles": [
            "srmp-ai-orchestrator/app/governance_data/tools.json",
            "srmp-ai-orchestrator/app/governance_data/capabilities.json",
            "srmp-agent/src/main/java/com/smartroad/srmp/agent/tool",
        ],
        "steps": [
            "在 tools.json 定义工具元数据、输入字段、输出契约和治理可见性",
            "在 Java AiToolRegistry 注册实现并暴露到 Tool Gateway",
            "在 capabilities.json 配置哪些能力 required/optional/adaptive/prohibited 该工具",
            "补充策略样例，覆盖工具必须调用和禁止误调用的场景",
            "通过工具详情确认 Java 注册、Runtime 白名单和影响能力一致",
        ],
        "verificationCommands": [
            "PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator/tests/test_governance_api.py -v",
            "curl -s http://127.0.0.1:8080/api/agent/orchestrator/ops/governance/tools/<toolName>?includeContract=true",
        ],
    }


def _contract_readiness_issues(tool_rows: List[Dict[str, Any]], contract: Dict[str, Any]) -> List[Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []
    missing_in_java = set(_string_list(contract.get("missingInJava")))
    blocked_by_runtime = set(_string_list(contract.get("blockedByRuntimeWhitelist")))
    write_blocked = set(_string_list(contract.get("writeBlocked")))
    declared_tools = {str(tool.get("name") or ""): tool for tool in tool_rows}
    for tool_name, tool in declared_tools.items():
        affected = int(tool.get("affectedCapabilityCount") or 0)
        severity = "ERROR" if affected > 0 else "WARN"
        if tool_name in missing_in_java:
            issues.append(_readiness_issue(
                severity,
                "TOOL_MISSING_IN_JAVA",
                "CONTRACT",
                "工具已配置但 Java Tool Gateway 未注册：" + tool_name,
                tool_name=tool_name,
            ))
        if tool_name in blocked_by_runtime:
            issues.append(_readiness_issue(
                severity,
                "TOOL_BLOCKED_BY_RUNTIME",
                "CONTRACT",
                "工具已注册但未进入 Runtime 白名单：" + tool_name,
                tool_name=tool_name,
            ))
        if tool_name in write_blocked and affected > 0:
            issues.append(_readiness_issue(
                "WARN",
                "WRITE_TOOL_BLOCKED",
                "CONTRACT",
                "写风险工具被 Runtime 拦截：" + tool_name,
                tool_name=tool_name,
            ))

    java_tools = set(_string_list(contract.get("javaTools")))
    for tool_name in sorted(java_tools - set(declared_tools.keys())):
        issues.append(_readiness_issue(
            "WARN",
            "JAVA_TOOL_NOT_DECLARED",
            "CONTRACT",
            "Java Tool Gateway 存在未纳入治理配置的工具：" + tool_name,
            tool_name=tool_name,
        ))
    return issues


def _contract_readiness_summary(contract: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not isinstance(contract, dict):
        return {"checked": False}
    return {
        "checked": True,
        "gatewayOk": bool(contract.get("ok")),
        "javaToolCount": len(_string_list(contract.get("javaTools"))),
        "runtimeAllowedToolCount": len(_string_list(contract.get("runtimeAllowedTools"))),
        "missingInJavaCount": len(_string_list(contract.get("missingInJava"))),
        "blockedByRuntimeCount": len(_string_list(contract.get("blockedByRuntimeWhitelist"))),
        "writeBlockedCount": len(_string_list(contract.get("writeBlocked"))),
    }


def _readiness_issue(
    severity: str,
    issue_code: str,
    source: str,
    message: str,
    code: Optional[str] = None,
    tool_name: str = "",
    capability_id: str = "",
    case_id: str = "",
) -> Dict[str, Any]:
    return {
        "severity": severity,
        "code": issue_code,
        "source": source,
        "message": message,
        "detailCode": code or issue_code,
        "toolName": tool_name,
        "capabilityId": capability_id,
        "caseId": case_id,
    }


def _severity_counts(issues: List[Dict[str, Any]]) -> Dict[str, int]:
    return {
        "ERROR": sum(1 for item in issues if item.get("severity") == "ERROR"),
        "WARN": sum(1 for item in issues if item.get("severity") == "WARN"),
        "INFO": sum(1 for item in issues if item.get("severity") == "INFO"),
    }


def _normalize_policy_example(value: Dict[str, Any], capability_id: str) -> Dict[str, Any]:
    data = dict(value or {})
    data["id"] = str(data.get("id") or capability_id + ".example").strip()
    data["name"] = str(data.get("name") or data["id"]).strip()
    data["request"] = dict(data.get("request") or {})
    expect = dict(data.get("expect") or {})
    expect["capabilityId"] = str(expect.get("capabilityId") or capability_id).strip()
    for key in ("requiredTools", "prohibitedTools", "exactToolNames"):
        expect[key] = _string_list(expect.get(key))
    data["expect"] = expect
    return data


def _string_list(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item or "").strip()]


def _first_string(data: Dict[str, Any], *keys: str) -> Optional[str]:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return _first_string(raw, *keys)
    return None
