#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "srmp-web-ui/src/api/solution.ts"
def fail(msg):
    print("[FAIL] " + msg); sys.exit(1)
if not API.exists(): fail("文件不存在：" + str(API))
bak = API.with_suffix(API.suffix + ".phase38.bak")
if not bak.exists(): bak.write_text(API.read_text(encoding="utf-8"), encoding="utf-8")
s = API.read_text(encoding="utf-8")
if "AiSolutionAiContextUpdateRequest" not in s:
    marker = "export interface AiSolutionDraftUpdateRequest {"
    insert = """export interface AiSolutionAiContextUpdateRequest {
  aiTraceId?: string
  aiAnswer?: string
  aiSources?: Record<string, any>[]
  aiToolResults?: Record<string, any>[]
  aiEvidence?: Record<string, any>
  aiContext?: Record<string, any>
  generationMode?: string
}

"""
    if marker not in s: fail("无法定位 AiSolutionDraftUpdateRequest")
    s = s.replace(marker, insert + marker, 1)
if "updateSolutionTaskAiContext" not in s:
    s = s.rstrip() + """

export function updateSolutionTaskAiContext(id: string, data: AiSolutionAiContextUpdateRequest): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/ai-context`, data)
}

export function getSolutionTaskAiContext(id: string): Promise<Record<string, any>> {
  return request.get(`/api/ai/solution/tasks/${id}/ai-context`)
}

export function getSolutionTaskStatusTimeline(id: string): Promise<Record<string, any>[]> {
  return request.get(`/api/ai/solution/tasks/${id}/status-timeline`)
}

export function restoreSolutionTaskVersion(id: string, versionNo: number, changeNote?: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/versions/${versionNo}/restore`, { changeNote })
}

export function getSolutionMarkdownV2ExportUrl(id: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return `${base}/api/ai/solution/tasks/${id}/export/markdown-v2`
}
"""
API.write_text(s, encoding="utf-8")
print("[OK] patched " + str(API))
