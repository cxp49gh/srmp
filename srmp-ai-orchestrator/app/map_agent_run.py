import time
from typing import Any, Dict, List, Optional

from .java_tools import JavaToolGateway, extract_business_sources, extract_knowledge_sources, merge_sources
from .governance import governance_tool_policy_result, normalize_tool_policy, resolve_capability
from .live_trace import LiveTraceStore
from .observability import _business_scope_from_request
from .plan_execution import build_plan_execution, normalize_plan_preview
from .schemas import (
    ActionResult,
    MapAgentRunRequest,
    MapAgentRunResponse,
    MapAiAgentRequest,
    SuggestedAction,
    ToolCall,
    ToolResult,
)
from .workflow import LangGraphWorkflow
from .planner import plan_tools


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
        self._start_live_trace(resolved_trace_id, normalize_action(request.action), "solution_generation_graph", request)
        agent_request = self._to_agent_request(request, request.action)
        capability = resolve_capability(
            agent_request,
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": normalize_action(request.action)},
        )
        evidence_started_at = time.perf_counter()
        self._start_live_step(resolved_trace_id, "solution_evidence_collect", "查询业务证据")
        try:
            evidence_results, planned_evidence_tool_names, blocked_tool_names = await self._collect_solution_evidence(
                agent_request,
                tenant_id or "default",
                resolved_trace_id,
                capability,
            )
        except Exception as exc:
            self._fail_live_step(resolved_trace_id, "solution_evidence_collect", str(exc))
            raise
        business_evidence = self._business_evidence_from_results(evidence_results)
        evidence_tool_policy = governance_tool_policy_result(
            capability,
            planned_tool_names=planned_evidence_tool_names,
            executable_tool_names=[item.toolName for item in evidence_results],
            blocked_tool_names=blocked_tool_names,
        )
        self._complete_live_step(resolved_trace_id, {
            "name": "solution_evidence_collect",
            "label": "查询业务证据",
            "status": "SUCCESS",
            "count": business_evidence.get("toolSuccessCount", 0),
            "elapsedMs": int((time.perf_counter() - evidence_started_at) * 1000),
            "data": {
                "toolNames": [item.toolName for item in evidence_results],
                "businessHitCount": business_evidence.get("businessHitCount", 0),
                "knowledgeHitCount": business_evidence.get("knowledgeHitCount", 0),
                "toolPolicy": evidence_tool_policy,
            },
        })
        self._start_live_step(resolved_trace_id, "solution_generate", "生成方案预览")
        try:
            tool_result = await self.gateway.execute_tool(
                call=ToolCall(toolName="solution.generateDraft", args=self._solution_args(request, evidence_results), reason="LangGraph-first solution preview"),
                request=agent_request,
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
        answer_meta = dict(answer_meta)
        tool_results = list(evidence_results) + [tool_result]
        tool_policy = governance_tool_policy_result(
            capability,
            planned_tool_names=list(planned_evidence_tool_names) + ["solution.generateDraft"],
            executable_tool_names=[item.toolName for item in tool_results],
            blocked_tool_names=blocked_tool_names,
        )
        answer_meta.update({
            "capabilityId": capability.get("capabilityId"),
            "capabilityName": capability.get("name"),
            "capabilityIntent": capability.get("intent"),
            "capabilityContextUsage": capability.get("contextUsage"),
            "policyStatus": tool_policy.get("policyStatus"),
            "policyChecks": tool_policy.get("policyChecks") or [],
        })
        raw_source_summaries = data.get("sourceSummaries") if isinstance(data.get("sourceSummaries"), list) else []
        source_summaries = [self._enrich_source_summary(item, request) for item in raw_source_summaries if isinstance(item, dict)]
        business_sources = extract_business_sources(evidence_results)
        knowledge_sources = extract_knowledge_sources(evidence_results)
        merged_sources = merge_sources(list(source_summaries), business_sources, knowledge_sources)
        tool_success_count = sum(1 for item in tool_results if item.success)
        tool_failed_count = sum(1 for item in tool_results if not item.success)
        response = MapAgentRunResponse(
            answer=answer,
            action=normalize_action(request.action),
            intent="SOLUTION_GENERATE",
            mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
            actionResult=action_result,
            suggestedActions=[SuggestedAction(action="SAVE_SOLUTION_DRAFT", label="保存为方案任务", requiresConfirmation=True)] if action_result.status == "SUCCESS" else [],
            toolResults=[item.model_dump(exclude_none=True) for item in tool_results],
            sources=merged_sources,
            answerMeta=answer_meta,
            trace=data.get("trace") if isinstance(data.get("trace"), dict) else {
                "steps": [{"name": "solution_generate", "label": "生成方案预览", "status": action_result.status, "count": 1 if tool_result.success else 0}]
            },
            data={
                "orchestratorProvider": "langgraph",
                "graphName": "solution_generation_graph",
                "capabilityId": capability.get("capabilityId"),
                "capability": capability,
                "toolTotalCount": len(tool_results),
                "toolSuccessCount": tool_success_count,
                "toolFailedCount": tool_failed_count,
                "sourceCount": len(merged_sources),
                "actionResultStatus": action_result.status,
                "businessEvidence": business_evidence,
                "toolPolicy": tool_policy,
            },
        )
        response.trace = dict(response.trace or {})
        response.trace["capability"] = capability
        response.trace["toolPolicy"] = tool_policy
        self._complete_live_step(resolved_trace_id, {
            "name": "solution_generate",
            "label": "生成方案预览",
            "status": action_result.status,
            "count": 1 if tool_result.success else 0,
            "elapsedMs": 0,
            "data": {"toolName": "solution.generateDraft", "toolPolicy": tool_policy},
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
        self._start_live_trace(resolved_trace_id, "SAVE_SOLUTION_DRAFT", "draft_save_graph", request)
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

    async def _collect_solution_evidence(
        self,
        agent_request: MapAiAgentRequest,
        tenant_id: Optional[str],
        trace_id: Optional[str],
        capability: Optional[Dict[str, Any]] = None,
    ) -> tuple[List[ToolResult], List[str], List[str]]:
        calls = [
            call for call in plan_tools(agent_request, "SOLUTION_GENERATE", {})
            if call.toolName not in {"solution.generateDraft", "template.match"}
        ]
        planned_tool_names = [call.toolName for call in calls]
        calls, blocked_tool_names = self._filter_prohibited_tool_calls(calls, capability or {})
        results: List[ToolResult] = []
        for call in calls:
            results.append(await self.gateway.execute_tool(call=call, request=agent_request, tenant_id=tenant_id or "default", trace_id=trace_id))
        return results, planned_tool_names, blocked_tool_names

    def _filter_prohibited_tool_calls(self, calls: List[ToolCall], capability: Dict[str, Any]) -> tuple[List[ToolCall], List[str]]:
        policy = normalize_tool_policy((capability or {}).get("toolPolicy") or {})
        prohibited = set(policy.get("prohibited") or [])
        if not prohibited:
            return calls, []
        allowed: List[ToolCall] = []
        blocked: List[str] = []
        for call in calls or []:
            if call.toolName in prohibited:
                blocked.append(call.toolName)
                continue
            allowed.append(call)
        return allowed, _unique_list(blocked)

    def _solution_args(self, request: MapAgentRunRequest, evidence_results: Optional[List[ToolResult]] = None) -> Dict[str, Any]:
        args = dict(request.actionInput or {})
        args["action"] = normalize_action(request.action)
        business_evidence = self._business_evidence_from_results(evidence_results or [])
        if business_evidence:
            args["businessEvidence"] = business_evidence
        if request.mapContext:
            args["mapContext"] = request.mapContext.model_dump(exclude_none=True)
        return args

    def _business_evidence_from_results(self, results: List[ToolResult]) -> Dict[str, Any]:
        if not results:
            return {}
        success = [item for item in results if item.success]
        failed = [item for item in results if not item.success]
        tool_summary = []
        business_hit_count = 0
        knowledge_hit_count = 0
        for item in results:
            hit_count = item.count if isinstance(item.count, int) else _estimate_hit_count(item.data)
            if item.success and item.toolName == "knowledge.retrieve":
                knowledge_hit_count += hit_count
            elif item.success:
                business_hit_count += hit_count
            tool_summary.append({
                "toolName": item.toolName,
                "success": item.success,
                "hitCount": hit_count,
                "summary": item.summary or item.reason or "",
                "error": item.errorMessage or item.error or "",
                "data": item.data,
            })
        return {
            "toolSuccessCount": len(success),
            "toolFailedCount": len(failed),
            "businessHitCount": business_hit_count,
            "knowledgeHitCount": knowledge_hit_count,
            "toolSummary": tool_summary,
        }

    def _enrich_source_summary(self, source: Dict[str, Any], request: MapAgentRunRequest) -> Dict[str, Any]:
        enriched = dict(source or {})
        if _has_locatable_source(enriched):
            return enriched
        target = _request_map_target(request)
        if not target:
            return enriched
        enriched["mapTarget"] = target
        title = enriched.get("sourceTitle") or enriched.get("source_title") or enriched.get("title") or target.get("title") or "业务来源"
        excerpt = enriched.get("contentExcerpt") or enriched.get("content_excerpt") or enriched.get("content") or enriched.get("summary") or ""
        followup = enriched.get("followupContext") if isinstance(enriched.get("followupContext"), dict) else {}
        enriched["followupContext"] = {
            **followup,
            "sourceId": enriched.get("sourceId") or enriched.get("source_id") or target.get("objectId") or target.get("routeCode"),
            "sourceType": enriched.get("sourceType") or enriched.get("source_type"),
            "sourceTitle": title,
            "contentExcerpt": excerpt,
            "excerpt": excerpt,
            "mapTarget": target,
        }
        return enriched

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
                "toolPlan": (response.data or {}).get("toolPlan") or [],
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

    def _start_live_trace(self, trace_id: str, action: str, graph_name: str, request: Optional[MapAgentRunRequest] = None) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.start_trace(trace_id, action=action, graph_name=graph_name, business_scope=_business_scope_from_request(request))

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


def _unique_list(values: List[Any]) -> List[str]:
    result: List[str] = []
    for value in values or []:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
    return result


def _estimate_hit_count(data: Any) -> int:
    if isinstance(data, dict):
        for key in ("totalCount", "returnedCount", "count"):
            value = data.get(key)
            if isinstance(value, int):
                return value
            try:
                if value not in (None, ""):
                    return int(value)
            except Exception:
                pass
        for key in ("items", "hits", "rows", "results"):
            value = data.get(key)
            if isinstance(value, list):
                return len(value)
        return 1 if data else 0
    if isinstance(data, list):
        return len(data)
    return 1 if data else 0


def _request_map_target(request: MapAgentRunRequest) -> Dict[str, Any]:
    action = normalize_action(request.action)
    ctx = request.mapContext.model_dump(exclude_none=True) if request.mapContext else {}
    action_input = request.actionInput or {}
    obj = action_input.get("mapObject") if isinstance(action_input.get("mapObject"), dict) else ctx.get("mapObject") if isinstance(ctx.get("mapObject"), dict) else {}
    geometry = action_input.get("geometry") or ctx.get("geometry") or ctx.get("regionGeometry")
    route_code = _first_value(action_input, "routeCode", "route_code") or _first_value(ctx, "routeCode", "route_code")
    if obj:
        object_type = _normalize_target_object_type(_first_value(obj, "objectType", "object_type", "type", "layerType"))
        object_id = _first_value(obj, "objectId", "object_id", "id")
        target = _compact({
            "objectType": object_type,
            "objectId": str(object_id) if object_id not in (None, "") else None,
            "id": str(object_id) if object_id not in (None, "") else None,
            "routeCode": _first_value(obj, "routeCode", "route_code", "route", "routeNo", "route_no") or route_code,
            "startStake": _first_value(obj, "startStake", "start_stake", "stakeStart", "stake_start"),
            "endStake": _first_value(obj, "endStake", "end_stake", "stakeEnd", "stake_end"),
            "title": _target_title(obj, object_type),
        })
        if _has_locatable_target(target):
            return target
    if action == "GENERATE_REGION_SOLUTION" or geometry:
        target = _compact({
            "objectType": "MAP_REGION",
            "routeCode": route_code,
            "geometry": geometry,
            "bbox": _first_value(action_input, "bbox", "bounds") or _first_value(ctx, "bbox", "bounds"),
            "title": "地图框选区域",
        })
        if _has_locatable_target(target):
            return target
    if action == "GENERATE_ROUTE_REPORT" or str(ctx.get("mode") or "").upper() == "ROUTE":
        target = _compact({
            "objectType": "ROAD_ROUTE",
            "routeCode": route_code,
            "title": f"路线 {route_code}" if route_code else "路线",
        })
        if _has_locatable_target(target):
            return target
    return {}


def _has_locatable_source(source: Dict[str, Any]) -> bool:
    target = source.get("mapTarget") or source.get("map_target")
    if not isinstance(target, dict):
        followup = source.get("followupContext") or source.get("followup_context")
        if isinstance(followup, dict):
            target = followup.get("mapTarget") or followup.get("map_target")
    return isinstance(target, dict) and _has_locatable_target(target)


def _has_locatable_target(target: Dict[str, Any]) -> bool:
    return any(target.get(key) not in (None, "", [], {}) for key in ("objectId", "routeCode", "startStake", "endStake", "geometry", "bbox"))


def _first_value(data: Dict[str, Any], *keys: str) -> Any:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    raw = data.get("raw")
    if isinstance(raw, dict):
        return _first_value(raw, *keys)
    return None


def _compact(data: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in data.items() if value not in (None, "", [], {})}


def _normalize_target_object_type(value: Any) -> str:
    raw = str(value or "").strip().upper()
    aliases = {
        "ASSESSMENT": "ASSESSMENT_RESULT",
        "ASSESSMENT_RESULT_RECORD": "ASSESSMENT_RESULT",
        "DISEASE_RECORD": "DISEASE",
        "ROADROUTE": "ROAD_ROUTE",
        "ROADSECTION": "ROAD_SECTION",
    }
    return aliases.get(raw, raw)


def _target_title(obj: Dict[str, Any], object_type: str) -> str:
    route_code = _first_value(obj, "routeCode", "route_code", "route")
    if object_type == "ROAD_ROUTE":
        return str(_first_value(obj, "routeName", "route_name", "name") or route_code or "路线")
    if object_type == "ROAD_SECTION":
        return str(_first_value(obj, "sectionName", "section_name", "sectionCode", "section_code") or route_code or "路段")
    if object_type == "DISEASE":
        return str(_first_value(obj, "diseaseName", "disease_name", "diseaseType", "disease_type") or "病害")
    if object_type == "ASSESSMENT_RESULT":
        return str(_first_value(obj, "unitCode", "unit_code", "objectId", "object_id", "id") or "评定结果")
    return str(route_code or object_type or "地图对象")
