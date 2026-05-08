from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field


class MapAiContext(BaseModel):
    # 允许前端继续传入 geometry / bounds / selectedFeature 等临时字段，避免经 Runtime 解析后被丢弃。
    model_config = ConfigDict(extra="allow")

    tenantId: Optional[str] = None
    mode: Optional[str] = None
    routeCode: Optional[str] = None
    year: Optional[int] = None
    mapObject: Optional[Dict[str, Any]] = None
    regionSummary: Optional[Dict[str, Any]] = None
    viewport: Optional[Dict[str, Any]] = None
    geometry: Optional[Dict[str, Any]] = None
    selectedLayers: Optional[List[str]] = None
    nearbyObjects: Optional[List[Dict[str, Any]]] = None
    userQuestion: Optional[str] = None
    extra: Optional[Dict[str, Any]] = None


class MapAiAgentRequest(BaseModel):
    # 兼容 Java MapAiAgentRequest、前端旧版 context/mapObject 直传，以及后续调试字段。
    model_config = ConfigDict(extra="allow")

    message: Optional[str] = None
    mapContext: Optional[MapAiContext] = None
    context: Optional[Dict[str, Any]] = None
    mapObject: Optional[Dict[str, Any]] = None
    options: Dict[str, Any] = Field(default_factory=dict)


class ToolCall(BaseModel):
    model_config = ConfigDict(extra="allow")

    toolName: str
    args: Dict[str, Any] = Field(default_factory=dict)
    reason: Optional[str] = None


class ToolResult(BaseModel):
    model_config = ConfigDict(extra="allow")

    toolName: str
    success: bool = False
    summary: Optional[str] = None
    reason: Optional[str] = None
    data: Any = None
    count: Optional[int] = None
    errorMessage: Optional[str] = None
    error: Optional[str] = None
    costMs: Optional[int] = None


class MapAiAgentResponse(BaseModel):
    model_config = ConfigDict(extra="allow")

    answer: str
    mode: str = "LANGGRAPH_AGENT"
    intent: Optional[str] = None
    mapContext: Optional[Dict[str, Any]] = None
    toolResults: List[Dict[str, Any]] = Field(default_factory=list)
    knowledgeSources: List[Dict[str, Any]] = Field(default_factory=list)
    sources: List[Dict[str, Any]] = Field(default_factory=list)
    trace: Dict[str, Any] = Field(default_factory=dict)
    data: Dict[str, Any] = Field(default_factory=dict)


class SuggestedAction(BaseModel):
    model_config = ConfigDict(extra="allow")

    action: str
    label: str
    requiresConfirmation: bool = False
    disabled: bool = False
    reason: Optional[str] = None
    payload: Dict[str, Any] = Field(default_factory=dict)


class ActionResult(BaseModel):
    model_config = ConfigDict(extra="allow")

    type: str = "ANSWER"
    status: str = "SUCCESS"
    title: Optional[str] = None
    markdown: Optional[str] = None
    objectSummary: Dict[str, Any] = Field(default_factory=dict)
    regionSummary: Dict[str, Any] = Field(default_factory=dict)
    routeSummary: Dict[str, Any] = Field(default_factory=dict)
    templateMeta: Dict[str, Any] = Field(default_factory=dict)
    qualityCheck: Dict[str, Any] = Field(default_factory=dict)
    draftTask: Optional[Dict[str, Any]] = None
    errorMessage: Optional[str] = None


class MapAgentRunRequest(BaseModel):
    model_config = ConfigDict(extra="allow")

    message: Optional[str] = None
    action: str = "CHAT"
    mapContext: Optional[MapAiContext] = None
    actionInput: Dict[str, Any] = Field(default_factory=dict)
    options: Dict[str, Any] = Field(default_factory=dict)


class MapAgentRunResponse(BaseModel):
    model_config = ConfigDict(extra="allow")

    answer: str
    mode: str = "LANGGRAPH_MAP_AGENT"
    action: str
    intent: Optional[str] = None
    mapContext: Optional[Dict[str, Any]] = None
    actionResult: ActionResult = Field(default_factory=ActionResult)
    suggestedActions: List[SuggestedAction] = Field(default_factory=list)
    toolResults: List[Dict[str, Any]] = Field(default_factory=list)
    knowledgeSources: List[Dict[str, Any]] = Field(default_factory=list)
    sources: List[Dict[str, Any]] = Field(default_factory=list)
    answerMeta: Dict[str, Any] = Field(default_factory=dict)
    trace: Dict[str, Any] = Field(default_factory=dict)
    data: Dict[str, Any] = Field(default_factory=dict)
