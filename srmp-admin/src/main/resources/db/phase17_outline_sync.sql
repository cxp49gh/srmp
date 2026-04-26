-- 阶段十七：Outline 同步入库
CREATE TABLE IF NOT EXISTS outline_sync_task (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    collection_id   VARCHAR(200),
    collection_name VARCHAR(500),
    sync_mode       VARCHAR(30),
    status          VARCHAR(30),
    total_count     INTEGER DEFAULT 0,
    success_count   INTEGER DEFAULT 0,
    skip_count      INTEGER DEFAULT 0,
    fail_count      INTEGER DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_tenant
ON outline_sync_task(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_outline_source
ON knowledge_document(tenant_id, source_type, source_id);