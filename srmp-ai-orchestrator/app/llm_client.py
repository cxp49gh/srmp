import json
import time
from dataclasses import asdict, dataclass
from typing import Any, Dict, Optional

import httpx

from .config import settings


SYSTEM_PROMPT = "你是公路养护 GIS 智能分析助手，回答必须严谨、可追溯、不能编造。"


@dataclass
class LlmResult:
    answer: str = ""
    status: str = "SKIPPED"
    enabled: bool = False
    model: str = ""
    baseUrlConfigured: bool = False
    apiKeyConfigured: bool = False
    costMs: int = 0
    httpStatus: Optional[int] = None
    finishReason: Optional[str] = None
    answerChars: int = 0
    rawResponsePreview: str = ""
    errorType: str = ""
    errorMessage: str = ""

    @classmethod
    def skipped(cls, reason: str, error_type: str = "DISABLED") -> "LlmResult":
        return cls(
            status="SKIPPED",
            enabled=settings.use_llm,
            model=settings.llm_model,
            baseUrlConfigured=bool(settings.llm_base_url),
            apiKeyConfigured=bool(settings.llm_api_key),
            errorType=error_type,
            errorMessage=reason,
        )

    @classmethod
    def success(
        cls,
        answer: str,
        model: str = "",
        cost_ms: int = 0,
        finish_reason: Optional[str] = None,
        raw_response_preview: str = "",
        http_status: Optional[int] = None,
    ) -> "LlmResult":
        text = answer or ""
        return cls(
            answer=text,
            status="SUCCESS",
            enabled=True,
            model=model or settings.llm_model,
            baseUrlConfigured=bool(settings.llm_base_url),
            apiKeyConfigured=bool(settings.llm_api_key),
            costMs=cost_ms,
            httpStatus=http_status,
            finishReason=finish_reason,
            answerChars=len(text),
            rawResponsePreview=raw_response_preview,
        )

    @classmethod
    def failed(
        cls,
        error_type: str,
        error_message: str,
        model: str = "",
        cost_ms: int = 0,
        raw_response_preview: str = "",
        http_status: Optional[int] = None,
        finish_reason: Optional[str] = None,
    ) -> "LlmResult":
        return cls(
            status="FAILED",
            enabled=True,
            model=model or settings.llm_model,
            baseUrlConfigured=bool(settings.llm_base_url),
            apiKeyConfigured=bool(settings.llm_api_key),
            costMs=cost_ms,
            httpStatus=http_status,
            finishReason=finish_reason,
            rawResponsePreview=raw_response_preview,
            errorType=error_type,
            errorMessage=_sanitize_message(error_message),
        )

    def to_dict(self) -> Dict[str, Any]:
        return {key: value for key, value in asdict(self).items() if value not in (None, "")}


class LlmClient:
    def __init__(self):
        self._last_result: LlmResult = LlmResult.skipped("LLM 未调用")

    def diagnostics(self) -> Dict[str, Any]:
        return self._last_result.to_dict()

    async def chat(self, prompt: str) -> Optional[str]:
        result = await self.generate(prompt)
        return result.answer if result.status == "SUCCESS" and result.answer else None

    async def probe(self) -> Dict[str, Any]:
        result = await self.generate("请只返回 OK", system_prompt="你是健康检查助手，只返回 OK。")
        data = result.to_dict()
        data["available"] = result.status == "SUCCESS"
        data["probeAnswerPreview"] = _preview(result.answer)
        data["probeCostMs"] = result.costMs
        data["connectTimeoutSeconds"] = settings.llm_connect_timeout_seconds
        data["readTimeoutSeconds"] = settings.llm_read_timeout_seconds
        data["maxTokens"] = settings.llm_max_tokens
        data["temperature"] = settings.llm_temperature
        return data

    async def generate(self, prompt: str, system_prompt: Optional[str] = None) -> LlmResult:
        if not settings.use_llm:
            self._last_result = LlmResult.skipped("SRMP_LANGGRAPH_USE_LLM=false", "DISABLED")
            return self._last_result
        missing = []
        if not settings.llm_base_url:
            missing.append("SRMP_LLM_BASE_URL")
        if not settings.llm_api_key:
            missing.append("SRMP_LLM_API_KEY")
        if not settings.llm_model:
            missing.append("SRMP_LLM_MODEL")
        if missing:
            self._last_result = LlmResult.failed("MISCONFIGURED", "缺少配置：" + ", ".join(missing))
            return self._last_result

        started = time.perf_counter()
        url = settings.llm_base_url.rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {settings.llm_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": settings.llm_model,
            "temperature": settings.llm_temperature,
            "max_tokens": settings.llm_max_tokens,
            "messages": [
                {"role": "system", "content": system_prompt or SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
        }
        timeout = httpx.Timeout(
            float(settings.llm_read_timeout_seconds),
            connect=float(settings.llm_connect_timeout_seconds),
        )

        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.post(url, json=payload, headers=headers)
            cost_ms = int((time.perf_counter() - started) * 1000)
            raw_preview = _preview(response.text)
            if response.status_code < 200 or response.status_code >= 300:
                self._last_result = LlmResult.failed(
                    "HTTP_ERROR",
                    "LLM HTTP 状态异常：" + str(response.status_code),
                    cost_ms=cost_ms,
                    raw_response_preview=raw_preview,
                    http_status=response.status_code,
                )
                return self._last_result
            body = response.json()
            self._last_result = _parse_response(body, cost_ms, response.status_code, raw_preview)
            return self._last_result
        except httpx.TimeoutException as exc:
            self._last_result = LlmResult.failed("TIMEOUT", str(exc), cost_ms=int((time.perf_counter() - started) * 1000))
            return self._last_result
        except json.JSONDecodeError as exc:
            self._last_result = LlmResult.failed(
                "PROVIDER_ERROR",
                "LLM 返回非 JSON：" + str(exc),
                cost_ms=int((time.perf_counter() - started) * 1000),
            )
            return self._last_result
        except Exception as exc:  # noqa: BLE001
            self._last_result = LlmResult.failed("UNKNOWN", str(exc), cost_ms=int((time.perf_counter() - started) * 1000))
            return self._last_result


def _parse_response(body: Any, cost_ms: int, http_status: int, raw_preview: str) -> LlmResult:
    if not isinstance(body, dict):
        return LlmResult.failed("PROVIDER_ERROR", "LLM 返回结构不是对象", cost_ms=cost_ms, raw_response_preview=raw_preview, http_status=http_status)
    choices = body.get("choices")
    if not isinstance(choices, list) or not choices:
        return LlmResult.failed("EMPTY_RESPONSE", "LLM 返回 choices 为空", cost_ms=cost_ms, raw_response_preview=raw_preview, http_status=http_status)
    first = choices[0] if isinstance(choices[0], dict) else {}
    finish_reason = first.get("finish_reason")
    message = first.get("message") if isinstance(first.get("message"), dict) else {}
    content = message.get("content")
    answer = _clean_answer(content) if isinstance(content, str) else ""
    if not answer:
        return LlmResult.failed(
            "EMPTY_RESPONSE",
            "LLM 返回为空",
            cost_ms=cost_ms,
            raw_response_preview=raw_preview,
            http_status=http_status,
            finish_reason=str(finish_reason) if finish_reason is not None else None,
        )
    return LlmResult.success(
        answer=answer,
        cost_ms=cost_ms,
        finish_reason=str(finish_reason) if finish_reason is not None else None,
        raw_response_preview=_preview(answer) or raw_preview,
        http_status=http_status,
    )


def _clean_answer(content: str) -> str:
    text = content.strip()
    for start, end in (("<think>", "</think>"), ("<thinking>", "</thinking>")):
        while start in text and end in text:
            prefix, rest = text.split(start, 1)
            _, suffix = rest.split(end, 1)
            text = (prefix + suffix).strip()
    return text


def _preview(text: Any) -> str:
    raw = "" if text is None else str(text)
    raw = raw.replace(settings.llm_api_key, "***") if settings.llm_api_key else raw
    limit = max(0, int(settings.llm_raw_preview_chars or 500))
    return raw[:limit]


def _sanitize_message(text: Any) -> str:
    return _preview(text)
