#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
ROUTER = ROOT / "srmp-web-ui/src/router/index.ts"
if not ROUTER.exists():
    print("[FAIL] 文件不存在：" + str(ROUTER)); sys.exit(1)
bak = ROUTER.with_suffix(ROUTER.suffix + ".phase39-3.bak")
if not bak.exists():
    bak.write_text(ROUTER.read_text(encoding="utf-8"), encoding="utf-8")
s = ROUTER.read_text(encoding="utf-8")
if "OutlineAutoSyncPage" not in s:
    s = s.replace("import OutlineSyncPage from '../views/agent/OutlineSyncPage.vue'\n",
                  "import OutlineSyncPage from '../views/agent/OutlineSyncPage.vue'\nimport OutlineAutoSyncPage from '../views/agent/OutlineAutoSyncPage.vue'\n")
    marker = """    {
      path: '/agent/outline-sync',
      component: OutlineSyncPage,
      meta: {
        title: 'Outline 同步入库'
      }
    },"""
    insert = marker + """
    {
      path: '/agent/outline-auto-sync',
      component: OutlineAutoSyncPage,
      meta: {
        title: 'Outline 自动同步'
      }
    },"""
    if marker not in s:
        print("[FAIL] 无法定位 /agent/outline-sync 路由"); sys.exit(1)
    s = s.replace(marker, insert, 1)
ROUTER.write_text(s, encoding="utf-8")
print("[OK] patched router")
