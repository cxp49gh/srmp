#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "srmp-web-ui/src/api/outline.ts"
if not API.exists():
    print("[FAIL] 文件不存在：" + str(API)); sys.exit(1)
bak = API.with_suffix(API.suffix + ".phase39-3.bak")
if not bak.exists():
    bak.write_text(API.read_text(encoding="utf-8"), encoding="utf-8")
s = API.read_text(encoding="utf-8")
append = """
export interface OutlineAutoSyncConfigRequest {
  id?: string
  name?: string
  enabled?: boolean
  collectionId?: string
  intervalMinutes?: number
  force?: boolean
  cleanupMissing?: boolean
  vectorizeAfterSync?: boolean
  vectorForce?: boolean
  vectorLimit?: number
  webhookEnabled?: boolean
  webhookSecret?: string
}

export interface OutlineAutoSyncRunRequest {
  triggerType?: string
  outlineEvent?: string
  outlineDocumentId?: string
  outlineCollectionId?: string
  force?: boolean
  vectorizeAfterSync?: boolean
}

export function getOutlineAutoSyncConfigs(): Promise<Record<string, any>[]> {
  return request.get('/api/outline/auto-sync/configs')
}

export function createOutlineAutoSyncConfig(data: OutlineAutoSyncConfigRequest): Promise<Record<string, any>> {
  return request.post('/api/outline/auto-sync/configs', data)
}

export function updateOutlineAutoSyncConfig(id: string, data: OutlineAutoSyncConfigRequest): Promise<Record<string, any>> {
  return request.put(`/api/outline/auto-sync/configs/${id}`, data)
}

export function runOutlineAutoSyncNow(id: string, data?: OutlineAutoSyncRunRequest): Promise<Record<string, any>> {
  return request.post(`/api/outline/auto-sync/configs/${id}/run`, data || { triggerType: 'MANUAL' })
}

export function getOutlineAutoSyncRuns(params?: { configId?: string; limit?: number }): Promise<Record<string, any>[]> {
  return request.get('/api/outline/auto-sync/runs', { params })
}

export function scanOutlineAutoSyncDue(): Promise<Record<string, any>> {
  return request.post('/api/outline/auto-sync/scan-due')
}
"""
if "getOutlineAutoSyncConfigs" not in s:
    s = s.rstrip() + "\n\n" + append
API.write_text(s, encoding="utf-8")
print("[OK] patched outline api")
