import os
from dataclasses import dataclass
from typing import List


def _int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError:
        return default


def _float_env(name: str, default: float) -> float:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    try:
        return float(value)
    except ValueError:
        return default


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _list_env(name: str, default: str) -> List[str]:
    value = os.getenv(name, default)
    return [item.strip() for item in value.split(",") if item.strip()]


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("SRMP_ORCHESTRATOR_APP_NAME", "srmp-langgraph-orchestrator")
    java_base_url: str = os.getenv("SRMP_JAVA_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
    java_tool_execute_path: str = os.getenv("SRMP_JAVA_TOOL_EXECUTE_PATH", "/api/agent/tools/execute")
    java_tool_list_path: str = os.getenv("SRMP_JAVA_TOOL_LIST_PATH", "/api/agent/tools")
    java_connect_timeout_seconds: int = _int_env("SRMP_JAVA_CONNECT_TIMEOUT_SECONDS", 10)
    java_read_timeout_seconds: int = _int_env("SRMP_JAVA_READ_TIMEOUT_SECONDS", 120)
    allow_write_tools: bool = _bool_env("SRMP_LANGGRAPH_ALLOW_WRITE_TOOLS", False)
    use_llm: bool = _bool_env("SRMP_LANGGRAPH_USE_LLM", False)
    llm_base_url: str = os.getenv("SRMP_LLM_BASE_URL", "").rstrip("/")
    llm_api_key: str = os.getenv("SRMP_LLM_API_KEY", "")
    llm_model: str = os.getenv("SRMP_LLM_MODEL", "")
    llm_temperature: float = _float_env("SRMP_LLM_TEMPERATURE", 0.2)
    llm_connect_timeout_seconds: int = _int_env("SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS", 10)
    llm_read_timeout_seconds: int = _int_env("SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS", 180)
    llm_max_tokens: int = _int_env("SRMP_LANGGRAPH_LLM_MAX_TOKENS", 2048)
    llm_compact_retry_enabled: bool = _bool_env("SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED", True)
    llm_raw_preview_chars: int = _int_env("SRMP_LANGGRAPH_LLM_RAW_PREVIEW_CHARS", 500)
    max_tool_items_in_prompt: int = _int_env("SRMP_LANGGRAPH_MAX_TOOL_ITEMS_IN_PROMPT", 8)

    strategy_version: str = os.getenv("SRMP_LANGGRAPH_STRATEGY_VERSION", "phase50.11-config-health-guard-v1")
    enable_context_enrich: bool = _bool_env("SRMP_LANGGRAPH_ENABLE_CONTEXT_ENRICH", True)
    enable_evidence_fusion: bool = _bool_env("SRMP_LANGGRAPH_ENABLE_EVIDENCE_FUSION", True)
    enable_quality_guard: bool = _bool_env("SRMP_LANGGRAPH_ENABLE_QUALITY_GUARD", True)
    parallel_tool_execution: bool = _bool_env("SRMP_LANGGRAPH_PARALLEL_TOOLS", True)
    max_parallel_tools: int = _int_env("SRMP_LANGGRAPH_MAX_PARALLEL_TOOLS", 4)
    max_tool_calls: int = _int_env("SRMP_LANGGRAPH_MAX_TOOL_CALLS", 6)
    min_answer_chars: int = _int_env("SRMP_LANGGRAPH_MIN_ANSWER_CHARS", 80)
    require_evidence_prefix: bool = _bool_env("SRMP_LANGGRAPH_REQUIRE_EVIDENCE_PREFIX", True)
    adaptive_planning_enabled: bool = _bool_env("SRMP_ADAPTIVE_PLANNING_ENABLED", True)
    max_adaptive_iterations: int = max(0, min(_int_env("SRMP_MAX_ADAPTIVE_ITERATIONS", 1), 1))

    audit_max_records: int = _int_env("SRMP_LANGGRAPH_AUDIT_MAX_RECORDS", 200)
    audit_persist_enabled: bool = _bool_env("SRMP_LANGGRAPH_AUDIT_PERSIST_ENABLED", False)
    audit_persist_path: str = os.getenv("SRMP_LANGGRAPH_AUDIT_PERSIST_PATH", "/tmp/srmp-langgraph-runtime-audit.jsonl")
    audit_load_on_start: bool = _bool_env("SRMP_LANGGRAPH_AUDIT_LOAD_ON_START", True)
    audit_redact_enabled: bool = _bool_env("SRMP_LANGGRAPH_AUDIT_REDACT_ENABLED", True)
    audit_max_persist_bytes: int = _int_env("SRMP_LANGGRAPH_AUDIT_MAX_PERSIST_BYTES", 20 * 1024 * 1024)
    audit_prune_default_retain_latest: int = _int_env("SRMP_LANGGRAPH_AUDIT_PRUNE_DEFAULT_RETAIN_LATEST", 20)
    health_include_contract_default: bool = _bool_env("SRMP_LANGGRAPH_HEALTH_INCLUDE_CONTRACT_DEFAULT", True)
    health_include_gateway_default: bool = _bool_env("SRMP_LANGGRAPH_HEALTH_INCLUDE_GATEWAY_DEFAULT", True)
    health_persistence_warn_ratio: float = _float_env("SRMP_LANGGRAPH_HEALTH_PERSISTENCE_WARN_RATIO", 0.9)
    audit_redact_keys: List[str] = None  # type: ignore
    allowed_tools: List[str] = None  # type: ignore

    def __post_init__(self):
        object.__setattr__(
            self,
            "allowed_tools",
            _list_env(
                "SRMP_LANGGRAPH_ALLOWED_TOOLS",
                "knowledge.retrieve,gis.queryDiseases,gis.queryAssessmentResults,gis.queryDiseasesByStakeRange,gis.queryRegionSummary,gis.queryNearbyObjects,template.match,solution.generateDraft",
            ),
        )
        object.__setattr__(
            self,
            "audit_redact_keys",
            _list_env(
                "SRMP_LANGGRAPH_AUDIT_REDACT_KEYS",
                "authorization,token,api_key,apikey,password,secret,cookie,set-cookie,access_key,accesskey,private_key",
            ),
        )


settings = Settings()
