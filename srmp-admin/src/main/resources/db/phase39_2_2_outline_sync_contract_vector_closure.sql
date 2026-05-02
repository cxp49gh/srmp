-- Phase39.2.2：Outline 同步控制台契约收口与向量化闭环
ALTER TABLE outline_sync_task
ADD COLUMN IF NOT EXISTS dry_run BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS cleanup_missing BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS retry_task_id VARCHAR(64);

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

ALTER TABLE outline_sync_task_detail
ADD COLUMN IF NOT EXISTS outline_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS outline_updated_at VARCHAR(80),
ADD COLUMN IF NOT EXISTS content_chars INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS old_content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS detail_message TEXT,
ADD COLUMN IF NOT EXISTS document_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_task
ON outline_sync_task_detail(tenant_id, task_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_doc
ON outline_sync_task_detail(tenant_id, outline_document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_status_action
ON outline_sync_task_detail(tenant_id, task_id, status, action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_hash
ON outline_sync_task_detail(tenant_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_outline_collection
ON ai_knowledge_document((metadata->>'outlineCollectionId'))
WHERE source_type='OUTLINE';
