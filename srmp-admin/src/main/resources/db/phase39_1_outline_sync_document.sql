-- Phase39.1：Outline 同步明细表，记录每篇文档的同步结果
CREATE TABLE IF NOT EXISTS outline_sync_document (
    id              VARCHAR(32)  PRIMARY KEY,
    tenant_id       VARCHAR(32)  NOT NULL,
    task_id         VARCHAR(32)  NOT NULL,
    source_id       VARCHAR(64)  NOT NULL,
    title           VARCHAR(512),
    status          VARCHAR(32)   NOT NULL,  -- SUCCESS/SKIPPED/FAILED
    error_message   TEXT,
    document_id     VARCHAR(32),              -- ai_knowledge_document.id
    chunk_count     INTEGER      DEFAULT 0,
    content_hash    VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outline_sync_doc_task ON outline_sync_document(tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_outline_sync_doc_source ON outline_sync_document(tenant_id, source_id);