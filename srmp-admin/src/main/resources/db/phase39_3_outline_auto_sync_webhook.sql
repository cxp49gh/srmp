CREATE TABLE IF NOT EXISTS outline_auto_sync_config (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    name                  VARCHAR(200),
    enabled               BOOLEAN DEFAULT FALSE,
    collection_id          VARCHAR(128),
    interval_minutes       INTEGER DEFAULT 60,
    force                 BOOLEAN DEFAULT FALSE,
    cleanup_missing        BOOLEAN DEFAULT FALSE,
    vectorize_after_sync   BOOLEAN DEFAULT TRUE,
    vector_force           BOOLEAN DEFAULT FALSE,
    vector_limit           INTEGER DEFAULT 500,
    webhook_enabled        BOOLEAN DEFAULT FALSE,
    webhook_secret         VARCHAR(200),
    last_run_at            TIMESTAMP,
    next_run_at            TIMESTAMP,
    last_task_id           VARCHAR(64),
    last_vector_status     VARCHAR(32),
    status                VARCHAR(32) DEFAULT 'IDLE',
    error_message          TEXT,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_config_due
ON outline_auto_sync_config(tenant_id, enabled, next_run_at);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_config_secret
ON outline_auto_sync_config(tenant_id, webhook_secret)
WHERE webhook_enabled = TRUE;

CREATE TABLE IF NOT EXISTS outline_auto_sync_run (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    config_id             VARCHAR(64),
    trigger_type           VARCHAR(32) NOT NULL,
    outline_event          VARCHAR(100),
    outline_document_id    VARCHAR(128),
    outline_collection_id  VARCHAR(128),
    sync_task_id           VARCHAR(64),
    vectorize_triggered    BOOLEAN DEFAULT FALSE,
    vectorize_status       VARCHAR(32),
    vectorize_message      TEXT,
    status                VARCHAR(32) DEFAULT 'RUNNING',
    error_message          TEXT,
    started_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at            TIMESTAMP,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_run_config
ON outline_auto_sync_run(tenant_id, config_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_run_doc
ON outline_auto_sync_run(tenant_id, outline_document_id, created_at DESC);
