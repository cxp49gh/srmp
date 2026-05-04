from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class MapAiContext(BaseModel):
    tenantId: Optional[str] = None
    mode: Optional[str] = None
    routeCode: Optional[str] = None
    year: Optional[int] = None
    mapObject: Optional[Dict[str, Any]] = None
    regionSummary: Optional[Dict[str, Any]] = None
    viewport: Optional[Dict[str, Any]] = None
    selectedLayers: Optional[List[str]] = None
    nearbyObjects: Optional[List[Dict[str, Any]]] = None
    userQuestion: Optional[str] = None
    extra: Optional[Dict[str, Any]] = None


class MapAiAgentRequest(BaseModel):
    message: Optional[str] = None
    mapContext: Optional[MapAiContext] = None
    context: Optional[Dict[str, Any]] = None
    mapObject: Optional[Dict[str, Any]] = None
    options: Dict[str, Any] = Field(default_factory=dict)


class ToolCall(BaseModel):
    toolName: str
    args: Dict[str, Any] = Field(default_factory=dict)
    reason: Optional[str] = None


class ToolResult(BaseModel):
    toolName: str
    success: bool = False
    summary: Optional[str] = None
    data: Any = None
    count: Optional[int] = None
    errorMessage: Optional[str] = None
    costMs: Optional[int] = None


class MapAiAgentResponse(BaseModel):
    answer: str
    mode: str = "LANGGRAPH_AGENT"
    intent: Optional[str] = None
    mapContext: Optional[Dict[str, Any]] = None
    toolResults: List[Dict[str, Any]] = Field(default_factory=list)
    knowledgeSources: List[Dict[str, Any]] = Field(default_factory=list)
    sources: List[Dict[str, Any]] = Field(default_factory=list)
    trace: Dict[str, Any] = Field(default_factory=dict)
    data: Dict[str, Any] = Field(default_factory=dict)
