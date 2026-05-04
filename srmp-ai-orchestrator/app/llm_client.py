from typing import Optional

import httpx

from .config import settings


class LlmClient:
    async def chat(self, prompt: str) -> Optional[str]:
        if not settings.use_llm:
            return None
        if not settings.llm_base_url or not settings.llm_api_key or not settings.llm_model:
            return None

        url = settings.llm_base_url.rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {settings.llm_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": settings.llm_model,
            "temperature": settings.llm_temperature,
            "messages": [
                {"role": "system", "content": "你是公路养护 GIS 智能分析助手，回答必须严谨、可追溯、不能编造。"},
                {"role": "user", "content": prompt},
            ],
        }
        async with httpx.AsyncClient(timeout=httpx.Timeout(120.0, connect=10.0)) as client:
            response = await client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            body = response.json()
        choices = body.get("choices") if isinstance(body, dict) else None
        if not choices:
            return None
        message = choices[0].get("message") or {}
        content = message.get("content")
        return _clean_answer(content) if content else None


def _clean_answer(content: str) -> str:
    text = content.strip()
    # 兼容部分模型错误透出的思考标签，避免前端直接展示内部推理。
    for start, end in (("<think>", "</think>"), ("<thinking>", "</thinking>")):
        while start in text and end in text:
            prefix, rest = text.split(start, 1)
            _, suffix = rest.split(end, 1)
            text = (prefix + suffix).strip()
    return text
