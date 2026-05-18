import time
from typing import Any, Dict, List, Optional

from .java_tools import JavaToolGateway
from .live_trace import LiveTraceStore
from .plan_execution import build_plan_execution, normalize_plan_preview
from .schemas import (
    ActionResult,
    MapAgentRunRequest,
    MapAgentRunResponse,
    MapAiAgentRequest,
    SuggestedAction,
    ToolCall,
)
from .workflow import LangGraphWorkflow


ANALYSIS_ACTIONS = {"CHAT", "ANALYZE_OBJECT", "ANALYZE_ROUTE", "ANALYZE_REGION", "PLAN_ONLY"}
SOLUTION_ACTIONS = {"GENERATE_OBJECT_SOLUTION", "GENERATE_REGION_SOLUTION", "GENERATE_ROUTE_REPORT"}


class MapAgentRunWorkflow:
    def __init__(self, base_workflow: LangGraphWorkflow, gateway: JavaToolGateway, live_trace_store: Optional[LiveTraceStore] = None):
        self.base_workflow = base_workflow
        self.gateway = gateway
        self.live_trace_store = live_trace_store

    async def run(
        self,
        request: MapAgentRunRequest,
        tenant_id: Optional[str],
        trace_id: Optional[str],
    ) -> MapAgentRunResponse:
        action = normalize_action(request.action)
        started_at = time.perf_counter()
        if action == "SAVE_SOLUTION_DRAFT":
            response = await self._run_draft_save(request, tenant_id, trace_id)
        elif action in SOLUTION_ACTIONS:
            response = await self._run_solution_generation(request, tenant_id, trace_id)
        else:
            response = await self._run_analysis(request, tenant_id, trace_id, action)
        response.data["action"] = action
        response.data["costMs"] = int((time.perf_counter() - started_at) * 1000)
        response.trace["action"] = action
        response.trace["graphName"] = response.data.get("graphName")
        response.trace["actionResultType"] = response.actionResult.type
        response.trace["suggestedActionCount"] = len(response.suggestedActions)
        self._attach_plan_execution(response, request, action)
        return response

    async def _run_analysis(
        self,
        request: MapAgentRunRequest,
        tenant_id: Optional[str],
        trace_id: Optional[str],
        action: str,
    ) -> MapAgentRunResponse:
        agent_request = self._to_agent_request(request, action)
        agent_response = await self.base_workflow.run(agent_request, tenant_id=tenant_id, trace_id=trace_id)
        result_type = "REGION_SUMMARY" if action == "ANALYZE_REGION" else "OBJECT_SUMMARY" if action == "ANALYZE_OBJECT" else "ANSWER"
        action_result = ActionResult(type=result_type, status="SUCCESS")
        if action == "ANALYZE_REGION" and agent_request.mapContext and agent_request.mapContext.regionSummary:
            action_result.regionSummary = agent_request.mapContext.regionSummary
        if action == "ANALYZE_OBJECT" and agent_request.mapContext and agent_request.mapContext.mapObject:
            action_result.objectSummary = agent_request.mapContext.mapObject
        return self._from_agent_response(
            request,
            agent_response,
            action,
            graph_name=graph_name_for(action),
            action_result=action_result,
        )

    async def _run_solution_generation(
        self,
        request: MapAgentRunRequest,
        tenant_id: Optional[str],
        trace_id: Optional[str],
    ) -> MapAgentRunResponse:
        resolved_trace_id = self._trace_id(request, trace_id)
        self._start_live_trace(resolved_trace_id, normalize_action(request.action), "solution_generation_graph")
        self._start_live_step(resolved_trace_id, "solution_generate", "生成方案预览")
        try:
            tool_result = await self.gateway.execute_tool(
                call=ToolCall(toolName="solution.generateDraft", args=self._solution_args(request), reason="LangGraph-first solution preview"),
                request=self._to_agent_request(request, request.action),
                tenant_id=tenant_id or "default",
                trace_id=resolved_trace_id,
            )
        except Exception as exc:
            self._fail_live_step(resolved_trace_id, "solution_generate", str(exc))
            raise
        data = tool_result.data if isinstance(tool_result.data, dict) else {}
        action_result = ActionResult(
            type="SOLUTION_PREVIEW",
            status="SUCCESS" if tool_result.success else "FAILED",
            title=str(data.get("title") or ""),
            markdown=str(data.get("markdown") or ""),
            objectSummary=data.get("objectSummary") if isinstance(data.get("objectSummary"), dict) else {},
            regionSummary=data.get("regionSummary") if isinstance(data.get("regionSummary"), dict) else {},
            routeSummary=data.get("routeSummary") if isinstance(data.get("routeSummary"), dict) else {},
            templateMeta=data.get("templateMeta") if isinstance(data.get("templateMeta"), dict) else {},
            qualityCheck=data.get("qualityCheck") if isinstance(data.get("qualityCheck"), dict) else {},
            errorMessage=tool_result.errorMessage or tool_result.error,
        )
        answer = action_result.markdown or tool_result.summary or action_result.errorMessage or "方案生成未返回内容"
        answer_meta = data.get("answerMeta") if isinstance(data.get("answerMeta"), dict) else {
            "answerSource": "TOOL_RESULT",
            "llmSuccess": False,
            "llmStatus": "SKIPPED",
        }
        source_summaries = data.get("sourceSummaries") if isinstance(data.get("sourceSummaries"), list) else []
        tool_success_count = 1 if tool_result.success else 0
        tool_failed_count = 0 if tool_result.success else 1
        response = MapAgentRunResponse(
            answer=answer,
            action=normalize_action(request.action),
            intent="SOLUTION_GENERATE",
            mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
            actionResult=action_result,
            suggestedActions=[SuggestedAction(action="SAVE_SOLUTION_DRAFT", label="保存为方案任务", requiresConfirmation=True)] if action_result.status == "SUCCESS" else [],
            toolResults=[tool_result.model_dump(exclude_none=True)],
            sources=source_summaries,
            answerMeta=answer_meta,
            trace=data.get("trace") if isinstance(data.get("trace"), dict) else {
                "steps": [{"name": "solution_generate", "label": "生成方案预览", "status": action_result.status, "count": 1 if tool_result.success else 0}]
            },
            data={
                "orchestratorProvider": "langgraph",
                "graphName": "solution_generation_graph",
                "toolTotalCount": 1,
                "toolSuccessCount": tool_success_count,
                "toolFailedCount": tool_failed_count,
                "sourceCount": len(source_summaries),
                "actionResultStatus": action_result.status,
            },
        )
        self._complete_live_step(resolved_trace_id, {
            "name": "solution_generate",
            "label": "生成方案预览",
            "status": action_result.status,
            "count": 1 if tool_result.success else 0,
            "elapsedMs": 0,
            "data": {"toolName": "solution.generateDraft"},
            "error": action_result.errorMessage,
        })
        self._complete_live_trace(resolved_trace_id, response, status=action_result.status)
        return response

    async def _run_draft_save(
        self,
        request: MapAgentRunRequest,
        tenant_id: Optional[str],
        trace_id: Optional[str],
    ) -> MapAgentRunResponse:
        resolved_trace_id = self._trace_id(request, trace_id)
        self._start_live_trace(resolved_trace_id, "SAVE_SOLUTION_DRAFT", "draft_save_graph")
        if not bool((request.options or {}).get("confirmed")):
            self._start_live_step(resolved_trace_id, "write_confirmation_check", "写入确认检查")
            response = MapAgentRunResponse(
                answer="保存方案任务需要确认。",
                action="SAVE_SOLUTION_DRAFT",
                intent="SOLUTION_SAVE",
                mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
                actionResult=ActionResult(type="DRAFT_SAVE", status="NEEDS_CONFIRMATION", errorMessage="options.confirmed 必须为 true"),
                suggestedActions=[SuggestedAction(action="SAVE_SOLUTION_DRAFT", label="确认保存方案任务", requiresConfirmation=True, payload=request.actionInput)],
                answerMeta={"answerSource": "SYSTEM", "llmSuccess": False, "llmStatus": "SKIPPED"},
                trace={"steps": [{"name": "write_confirmation_check", "label": "写入确认检查", "status": "SKIPPED", "data": {"confirmed": False}}]},
                data={"orchestratorProvider": "langgraph", "graphName": "draft_save_graph", "writeConfirmation": False},
            )
            self._complete_live_step(resolved_trace_id, {
                "name": "write_confirmation_check",
                "label": "写入确认检查",
                "status": "SKIPPED",
                "data": {"confirmed": False},
            })
            self._complete_live_trace(resolved_trace_id, response, status="NEEDS_CONFIRMATION")
            return response
        save_args = dict(request.actionInput or {})
        save_args["confirmed"] = True
        if request.mapContext:
            save_args["mapContext"] = request.mapContext.model_dump(exclude_none=True)
        self._start_live_step(resolved_trace_id, "solution_save_task", "保存方案任务")
        try:
            tool_result = await self.gateway.execute_tool(
                call=ToolCall(toolName="solution.saveTask", args=save_args, reason="Save confirmed solution draft"),
                request=self._to_agent_request(request, request.action),
                tenant_id=tenant_id or "default",
                trace_id=resolved_trace_id,
            )
        except Exception as exc:
            self._fail_live_step(resolved_trace_id, "solution_save_task", str(exc))
            raise
        data = tool_result.data if isinstance(tool_result.data, dict) else {}
        response = MapAgentRunResponse(
            answer=tool_result.summary or ("方案任务已保存" if tool_result.success else "方案任务保存失败"),
            action="SAVE_SOLUTION_DRAFT",
            intent="SOLUTION_SAVE",
            mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
            actionResult=ActionResult(type="DRAFT_SAVE", status="SUCCESS" if tool_result.success else "FAILED", draftTask=data, errorMessage=tool_result.errorMessage or tool_result.error),
            toolResults=[tool_result.model_dump(exclude_none=True)],
            answerMeta={"answerSource": "TOOL_RESULT", "llmSuccess": False, "llmStatus": "SKIPPED"},
            trace={"steps": [{"name": "solution_save_task", "label": "保存方案任务", "status": "SUCCESS" if tool_result.success else "FAILED", "count": 1 if tool_result.success else 0}]},
            data={"orchestratorProvider": "langgraph", "graphName": "draft_save_graph", "writeConfirmation": True},
        )
        status = "SUCCESS" if tool_result.success else "FAILED"
        self._complete_live_step(resolved_trace_id, {
            "name": "solution_save_task",
            "label": "保存方案任务",
            "status": status,
            "count": 1 if tool_result.success else 0,
            "data": {"toolName": "solution.saveTask"},
            "error": tool_result.errorMessage or tool_result.error,
        })
        self._complete_live_trace(resolved_trace_id, response, status=status)
        return response

    def _from_agent_response(
        self,
        request: MapAgentRunRequest,
        response: Any,
        action: str,
        graph_name: str,
        action_result: ActionResult,
    ) -> MapAgentRunResponse:
        data = response.data or {}
        sources = response.sources or response.knowledgeSources or []
        return MapAgentRunResponse(
            answer=response.answer,
            action=action,
            intent=response.intent,
            mapContext=response.mapContext,
            actionResult=action_result,
            suggestedActions=suggested_actions_for(action),
            toolResults=response.toolResults or [],
            knowledgeSources=response.knowledgeSources or [],
            sources=sources,
            answerMeta=data.get("answerMeta") or {},
            trace=response.trace or {},
            data={**data, "orchestratorProvider": "langgraph", "graphName": graph_name},
        )

    def _to_agent_request(self, request: MapAgentRunRequest, action: str) -> MapAiAgentRequest:
        options = dict(request.options or {})
        options["action"] = action
        return MapAiAgentRequest(message=request.message, mapContext=request.mapContext, options=options)

    def _solution_args(self, request: MapAgentRunRequest) -> Dict[str, Any]:
        args = dict(request.actionInput or {})
        args["action"] = normalize_action(request.action)
        if request.mapContext:
            args["mapContext"] = request.mapContext.model_dump(exclude_none=True)
        return args

    def _trace_id(self, request: MapAgentRunRequest, trace_id: Optional[str]) -> str:
        option_trace_id = (request.options or {}).get("traceId")
        return str(trace_id or option_trace_id or "").strip()

    def _attach_plan_execution(self, response: MapAgentRunResponse, request: MapAgentRunRequest, action: str) -> None:
        plan_execution = build_plan_execution(
            normalize_plan_preview((request.options or {}).get("planPreview")),
            {
                "runTraceId": self._trace_id(request, None),
                "actualAction": action,
                "actualIntent": response.intent,
                "toolResults": response.toolResults or [],
                "sources": response.sources or response.knowledgeSources or [],
                "evidence": (response.data or {}).get("evidence") or {},
                "adaptivePlanning": (response.data or {}).get("adaptivePlanning") or {},
            },
        )
        response.planExecution = plan_execution
        response.data["planExecution"] = plan_execution
        response.answerMeta = dict(response.answerMeta or {})
        response.answerMeta["planExecutionStatus"] = plan_execution.get("status")
        response.data["answerMeta"] = response.answerMeta
        response.trace = dict(response.trace or {})
        response.trace["planExecution"] = plan_execution

    def _start_live_trace(self, trace_id: str, action: str, graph_name: str) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.start_trace(trace_id, action=action, graph_name=graph_name)

    def _start_live_step(self, trace_id: str, name: str, label: str) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.start_step(trace_id, name, label)

    def _complete_live_step(self, trace_id: str, step: Dict[str, Any]) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.complete_step(trace_id, step)

    def _fail_live_step(self, trace_id: str, name: str, error: str) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.fail_step(trace_id, name, error)

    def _complete_live_trace(self, trace_id: str, response: MapAgentRunResponse, status: str = "SUCCESS") -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.complete_trace(trace_id, status=status, answer_meta=response.answerMeta, trace=response.trace)


def normalize_action(action: Optional[str]) -> str:
    normalized = str(action or "CHAT").strip().upper()
    allowed = ANALYSIS_ACTIONS | SOLUTION_ACTIONS | {"SAVE_SOLUTION_DRAFT"}
    return normalized if normalized in allowed else "CHAT"


def graph_name_for(action: str) -> str:
    if action == "ANALYZE_OBJECT":
        return "object_analysis_graph"
    if action == "ANALYZE_REGION":
        return "region_analysis_graph"
    if action == "ANALYZE_ROUTE":
        return "route_report_graph"
    if action == "PLAN_ONLY":
        return "plan_graph"
    return "chat_graph"


def suggested_actions_for(action: str) -> List[SuggestedAction]:
    if action == "ANALYZE_OBJECT":
        return [SuggestedAction(action="GENERATE_OBJECT_SOLUTION", label="生成对象方案", requiresConfirmation=False)]
    if action == "ANALYZE_REGION":
        return [SuggestedAction(action="GENERATE_REGION_SOLUTION", label="生成区域养护建议", requiresConfirmation=False)]
    if action == "ANALYZE_ROUTE":
        return [SuggestedAction(action="GENERATE_ROUTE_REPORT", label="生成路线报告", requiresConfirmation=False)]
    return []
