#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phase44 低冲突前端收口脚本

目标：基于最新 srmp 仓库做幂等增强，不整体覆盖现有大文件。
- 已存在的实现会自动跳过。
- 每个被修改文件会先生成 .phase44.bak 备份。
- 适配当前仓库里可能存在的一行 TS 文件，也适配格式化后的多行文件。

用法：
  cd srmp
  python3 scripts/apply_phase44_frontend_ops.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()
CHANGED = []
SKIPPED = []
WARNINGS = []


def path(rel: str) -> Path:
    return ROOT / rel


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def write_if_changed(p: Path, content: str) -> None:
    old = p.read_text(encoding="utf-8") if p.exists() else None
    if old == content:
        SKIPPED.append(f"{p}: no change")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    if p.exists():
        bak = p.with_suffix(p.suffix + ".phase44.bak")
        if not bak.exists():
            bak.write_text(old or "", encoding="utf-8")
    p.write_text(content, encoding="utf-8")
    CHANGED.append(str(p.relative_to(ROOT)))


def ensure_request_timeouts() -> None:
    p = path("srmp-web-ui/src/utils/request.ts")
    if not p.exists():
        WARNINGS.append(f"missing {p}")
        return
    s = read(p)
    changed = s
    if "VITE_API_TIMEOUT" not in changed:
        changed = changed.replace("const request = createRequest(30000)", "const request = createRequest(Number(import.meta.env.VITE_API_TIMEOUT || 60000))")
    if "export const longRequest" not in changed:
        if "export const aiRequest" in changed:
            changed = re.sub(
                r"(export\s+const\s+aiRequest\s*=\s*createRequest\([^\n;]+\);?)",
                r"\1\nexport const longRequest = createRequest(Number(import.meta.env.VITE_LONG_TIMEOUT || 300000))",
                changed,
                count=1,
            )
        else:
            changed += "\nexport const aiRequest = createRequest(Number(import.meta.env.VITE_AI_TIMEOUT || 300000))\nexport const longRequest = createRequest(Number(import.meta.env.VITE_LONG_TIMEOUT || 300000))\n"
    write_if_changed(p, changed)


def ensure_router_entry() -> None:
    p = path("srmp-web-ui/src/router/index.ts")
    if not p.exists():
        WARNINGS.append(f"missing {p}")
        return
    s = read(p)
    changed = s
    if "OutlineAutoSyncPage" not in changed:
        anchor = "import OutlineSyncPage from '../views/agent/OutlineSyncPage.vue'"
        import_line = "import OutlineAutoSyncPage from '../views/agent/OutlineAutoSyncPage.vue'"
        if anchor in changed:
            changed = changed.replace(anchor, anchor + " " + import_line, 1)
        else:
            changed = import_line + " " + changed
    if "/agent/outline-auto-sync" not in changed:
        route = "{ path: '/agent/outline-auto-sync', component: OutlineAutoSyncPage, meta: { title: 'Outline 自动同步' } },"
        anchor_route = "{ path: '/agent/outline-sync'"
        idx = changed.find(anchor_route)
        if idx >= 0:
            # 插入到 outline-sync route 之后的下一个 '},' 后面；兼容一行文件。
            end = changed.find("},", idx)
            if end >= 0:
                changed = changed[: end + 2] + " " + route + changed[end + 2 :]
            else:
                changed = changed.replace("routes: [", "routes: [ " + route, 1)
        else:
            changed = changed.replace("routes: [", "routes: [ " + route, 1)
    write_if_changed(p, changed)


def ensure_outline_api_long_requests() -> None:
    p = path("srmp-web-ui/src/api/outline.ts")
    if not p.exists():
        WARNINGS.append(f"missing {p}")
        return
    s = read(p)
    changed = s
    if "{ longRequest }" not in changed and "longRequest" not in changed.split("\n", 1)[0]:
        changed = changed.replace("import request from '../utils/request'", "import request, { longRequest } from '../utils/request'", 1)
    # 如果已经存在这些函数，则跳过；否则追加低冲突 API 封装。
    additions = []
    if "getOutlineKnowledgeStats" not in changed:
        additions.append("export function getOutlineKnowledgeStats(): Promise<Record<string, any>> { return request.get('/api/outline/knowledge-stats') }")
    if "vectorizeOutline" not in changed:
        additions.append("export function vectorizeOutline(data: { force?: boolean; dryRun?: boolean; limit?: number } = {}): Promise<Record<string, any>> { return longRequest.post('/api/outline/vectorize', data) }")
    if "getOutlineAutoSyncConfigs" not in changed:
        additions.append(dedent("""
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
        export function getOutlineAutoSyncConfigs(): Promise<Record<string, any>[]> { return request.get('/api/outline/auto-sync/configs') }
        export function createOutlineAutoSyncConfig(data: OutlineAutoSyncConfigRequest): Promise<Record<string, any>> { return request.post('/api/outline/auto-sync/configs', data) }
        export function updateOutlineAutoSyncConfig(id: string, data: OutlineAutoSyncConfigRequest): Promise<Record<string, any>> { return request.put(`/api/outline/auto-sync/configs/${id}`, data) }
        export function runOutlineAutoSyncNow(id: string, data?: Record<string, any>): Promise<Record<string, any>> { return longRequest.post(`/api/outline/auto-sync/configs/${id}/run`, data || { triggerType: 'MANUAL' }) }
        export function getOutlineAutoSyncRuns(params?: { configId?: string; limit?: number }): Promise<Record<string, any>[]> { return request.get('/api/outline/auto-sync/runs', { params }) }
        export function scanOutlineAutoSyncDue(): Promise<Record<string, any>> { return longRequest.post('/api/outline/auto-sync/scan-due') }
        export function testOutlineAutoSyncWebhook(secret: string, payload: Record<string, any>): Promise<Record<string, any>> { return longRequest.post('/api/outline/auto-sync/webhook', payload, { headers: { 'X-Outline-Webhook-Secret': secret } }) }
        """))
    if additions:
        changed = changed.rstrip() + "\n\n" + "\n".join(additions) + "\n"
    write_if_changed(p, changed)


def minimal_outline_auto_sync_page() -> str:
    return dedent(r'''
    <template>
      <div class="outline-auto-sync-page">
        <h2>Outline 自动同步</h2>
        <p class="desc">用于配置定时同步、Webhook 精准同步，以及同步后的向量化补齐。</p>
        <div class="actions">
          <el-button :loading="loading" type="primary" @click="loadAll">刷新</el-button>
          <el-button :loading="scanning" @click="scanDue">扫描到期配置</el-button>
        </div>
        <el-row :gutter="12" class="cards">
          <el-col :span="6"><el-card><div class="label">Outline 文档</div><div class="num">{{ stats.documentCount || 0 }}</div></el-card></el-col>
          <el-col :span="6"><el-card><div class="label">Chunk</div><div class="num">{{ stats.chunkCount || 0 }}</div></el-card></el-col>
          <el-col :span="6"><el-card><div class="label">已向量化</div><div class="num ok">{{ stats.embeddedChunkCount || 0 }}</div></el-card></el-col>
          <el-col :span="6"><el-card><div class="label">待补向量</div><div class="num warn">{{ stats.pendingEmbeddingChunkCount || 0 }}</div></el-card></el-col>
        </el-row>
        <el-card class="mt">
          <template #header>Webhook</template>
          <el-alert type="info" show-icon title="生产环境请配置 VITE_WEBHOOK_BASE_URL 为外部可访问的后端地址，例如 http://your-host:8080" />
          <el-input class="mt" :model-value="webhookUrl" readonly>
            <template #append><el-button @click="copyText(webhookUrl, 'Webhook 地址已复制')">复制</el-button></template>
          </el-input>
        </el-card>
        <el-card class="mt">
          <template #header>运行记录</template>
          <el-table :data="runs" border size="small">
            <el-table-column prop="triggerType" label="触发" width="110" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="outlineDocumentId" label="Outline 文档" min-width="220" />
            <el-table-column prop="syncTaskId" label="同步任务" min-width="220" />
            <el-table-column prop="vectorizeStatus" label="向量化" width="120" />
            <el-table-column prop="errorMessage" label="错误" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-card>
      </div>
    </template>

    <script setup lang="ts">
    import { computed, onMounted, ref } from 'vue'
    import { ElMessage } from 'element-plus'
    import { getOutlineAutoSyncRuns, getOutlineKnowledgeStats, scanOutlineAutoSyncDue } from '../../api/outline'

    const loading = ref(false)
    const scanning = ref(false)
    const stats = ref<Record<string, any>>({})
    const runs = ref<Record<string, any>[]>([])
    const webhookBaseUrl = computed(() => {
      const configured = String(import.meta.env.VITE_WEBHOOK_BASE_URL || import.meta.env.VITE_PUBLIC_API_ORIGIN || '').trim()
      return configured ? configured.replace(/\/$/, '') : location.origin
    })
    const webhookUrl = computed(() => `${webhookBaseUrl.value}/api/outline/auto-sync/webhook`)

    onMounted(loadAll)

    async function loadAll() {
      loading.value = true
      try {
        const [s, r] = await Promise.all([getOutlineKnowledgeStats(), getOutlineAutoSyncRuns({ limit: 100 })])
        stats.value = s || {}
        runs.value = r || []
      } finally {
        loading.value = false
      }
    }

    async function scanDue() {
      scanning.value = true
      try {
        await scanOutlineAutoSyncDue()
        ElMessage.success('扫描完成')
        await loadAll()
      } finally {
        scanning.value = false
      }
    }

    async function copyText(text: string, message: string) {
      try {
        await navigator.clipboard.writeText(text)
      } catch {
        const area = document.createElement('textarea')
        area.value = text
        area.style.position = 'fixed'
        area.style.opacity = '0'
        document.body.appendChild(area)
        area.select()
        document.execCommand('copy')
        document.body.removeChild(area)
      }
      ElMessage.success(message)
    }
    </script>

    <style scoped>
    .outline-auto-sync-page { padding: 18px; }
    .desc { color: #64748b; }
    .actions { display: flex; gap: 8px; margin: 12px 0; }
    .cards { margin: 12px 0; }
    .label { color: #64748b; font-size: 13px; }
    .num { font-size: 26px; font-weight: 800; margin-top: 8px; }
    .ok { color: #059669; }
    .warn { color: #d97706; }
    .mt { margin-top: 14px; }
    </style>
    ''').lstrip()


def ensure_outline_auto_sync_page_polish() -> None:
    p = path("srmp-web-ui/src/views/agent/OutlineAutoSyncPage.vue")
    if not p.exists() or p.stat().st_size == 0:
        write_if_changed(p, minimal_outline_auto_sync_page())
        return
    s = read(p)
    changed = s
    if "VITE_WEBHOOK_BASE_URL" not in changed:
        target = "const webhookUrl = computed(() => `${location.origin}/api/outline/auto-sync/webhook`)"
        replacement = dedent(r"""
        const webhookBaseUrl = computed(() => {
          const configured = String(import.meta.env.VITE_WEBHOOK_BASE_URL || import.meta.env.VITE_PUBLIC_API_ORIGIN || '').trim()
          return configured ? configured.replace(/\/$/, '') : location.origin
        })
        const webhookUrl = computed(() => `${webhookBaseUrl.value}/api/outline/auto-sync/webhook`)
        """).strip()
        if target in changed:
            changed = changed.replace(target, replacement, 1)
        else:
            WARNINGS.append("OutlineAutoSyncPage.vue: webhookUrl pattern not found; skipped webhook base-url patch")
    if "document.execCommand('copy')" not in changed:
        target = dedent("""
        async function copyText(text: string, message: string) {

         await navigator.clipboard.writeText(text)

         ElMessage.success(message)

        }
        """).strip()
        replacement = dedent("""
        async function copyText(text: string, message: string) {
          try {
            await navigator.clipboard.writeText(text)
          } catch {
            const area = document.createElement('textarea')
            area.value = text
            area.style.position = 'fixed'
            area.style.opacity = '0'
            document.body.appendChild(area)
            area.select()
            document.execCommand('copy')
            document.body.removeChild(area)
          }
          ElMessage.success(message)
        }
        """).strip()
        if target in changed:
            changed = changed.replace(target, replacement, 1)
        else:
            WARNINGS.append("OutlineAutoSyncPage.vue: copyText pattern not found; skipped clipboard fallback patch")
    write_if_changed(p, changed)


def ensure_env_example() -> None:
    # 优先写入 .env.example；没有则创建。不要修改开发者本地 .env.development 的密钥配置。
    p = path("srmp-web-ui/.env.example")
    content = read(p) if p.exists() else ""
    block = dedent("""
    # Phase44 AI / Outline long task timeout
    VITE_API_TIMEOUT=60000
    VITE_AI_TIMEOUT=300000
    VITE_LONG_TIMEOUT=300000
    # Outline Webhook 外部访问地址；前端 dev server 为 5173 时，建议填后端地址，例如 http://localhost:8080
    VITE_WEBHOOK_BASE_URL=http://localhost:8080
    """).strip()
    changed = content
    for line in block.splitlines():
        key = line.split("=", 1)[0]
        if line.startswith("#"):
            continue
        if key and key not in changed:
            changed += ("\n" if changed and not changed.endswith("\n") else "") + line + "\n"
    if "Phase44 AI / Outline" not in changed:
        changed = block + "\n" + changed
    write_if_changed(p, changed)


def write_notes() -> None:
    p = path("docs/phase44-ai-outline-frontend-low-conflict.md")
    content = dedent("""
    # Phase44 AI / Outline 前端低冲突收口说明

    本阶段不再整体替换 `OutlineAutoSyncPage.vue`、`router/index.ts`、`request.ts` 等大文件，而是通过 `scripts/apply_phase44_frontend_ops.py` 做幂等增强。

    ## 解决的问题

    1. 避免前端实现包与最新仓库已有 Outline 自动同步页面产生大面积冲突。
    2. 确认 `longRequest`、`VITE_AI_TIMEOUT`、`VITE_LONG_TIMEOUT` 生效，避免 AI/Outline 长任务前端提前超时。
    3. Webhook 地址支持 `VITE_WEBHOOK_BASE_URL`，避免前端运行在 `5173` 时复制出无法被 Outline 服务访问的地址。
    4. Clipboard API 不可用时增加 textarea fallback，复制按钮在非 HTTPS/部分浏览器下也可用。

    ## 使用方式

    ```bash
    cd srmp
    python3 scripts/apply_phase44_frontend_ops.py
    cd srmp-web-ui
    npm run build
    ```

    ## 推荐环境变量

    ```env
    VITE_API_TIMEOUT=60000
    VITE_AI_TIMEOUT=300000
    VITE_LONG_TIMEOUT=300000
    VITE_WEBHOOK_BASE_URL=http://localhost:8080
    ```
    """).lstrip()
    write_if_changed(p, content)


def main() -> int:
    if not path("srmp-web-ui").exists():
        print("ERROR: 请在 srmp 仓库根目录执行。", file=sys.stderr)
        return 2
    ensure_request_timeouts()
    ensure_outline_api_long_requests()
    ensure_router_entry()
    ensure_outline_auto_sync_page_polish()
    ensure_env_example()
    write_notes()

    print("\nPhase44 apply result")
    print("====================")
    if CHANGED:
        print("Changed:")
        for item in CHANGED:
            print(f"  - {item}")
    if SKIPPED:
        print("Skipped:")
        for item in SKIPPED[:20]:
            print(f"  - {item}")
    if WARNINGS:
        print("Warnings:")
        for item in WARNINGS:
            print(f"  - {item}")
    print("\nNext:")
    print("  cd srmp-web-ui && npm run build")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
