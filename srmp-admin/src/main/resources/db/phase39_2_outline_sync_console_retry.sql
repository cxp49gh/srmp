-- Phase39.2：Outline 同步明细、失败重试、过期清理
-- 说明：
-- 1. outline_sync_task_detail 记录每篇 Outline 文档同步结果；
-- 2. 支持前端查看失败原因；
-- 3. 支持按任务重试失败文档；
-- 4. 支持 cleanupMissing 将 Outline 已不可见文档标记为 INACTIVE。

CREATE TABLE IF NOT EXISTS outline_sync_task_detail (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    task_id               VARCHAR(64) NOT NULL,
    outline_document_id   VARCHAR(128),
    outline_title         VARCHAR(500),
    collection_id         VARCHAR(128),
    action                VARCHAR(64),
    status                VARCHAR(32),
    skip_reason           VARCHAR(500),
    error_type            VARCHAR(200),
    error_message         TEXT,
    knowledge_document_id VARCHAR(64),
    chunk_count           INTEGER DEFAULT 0,
    cost_ms               BIGINT DEFAULT 0,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_task
ON outline_sync_task_detail(tenant_id, task_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_doc
ON outline_sync_task_detail(tenant_id, outline_document_id, created_at DESC);

ALTER TABLE outline_sync_task
ADD COLUMN IF NOT EXISTS dry_run BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS cleanup_missing BOOLEAN DEFAULT FALSE;

-- 可选：为 Outline metadata 检索加索引。
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_outline_collection
ON ai_knowledge_document((metadata->>'outlineCollectionId'))
WHERE source_type='OUTLINE';
