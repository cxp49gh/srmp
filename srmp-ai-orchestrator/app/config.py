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
    llm_temperature: float = float(os.getenv("SRMP_LLM_TEMPERATURE", "0.2") or "0.2")
    max_tool_items_in_prompt: int = _int_env("SRMP_LANGGRAPH_MAX_TOOL_ITEMS_IN_PROMPT", 8)
    allowed_tools: List[str] = None  # type: ignore

    def __post_init__(self):
        object.__setattr__(
            self,
            "allowed_tools",
            _list_env(
                "SRMP_LANGGRAPH_ALLOWED_TOOLS",
                "knowledge.retrieve,gis.queryDiseases,gis.queryAssessmentResults,gis.queryDiseasesByStakeRange,gis.queryRegionSummary,gis.queryNearbyObjects,template.match",
            ),
        )


settings = Settings()
