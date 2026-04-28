-- Phase 33: AI solution draft versioning
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql

ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS object_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS map_object JSONB,
ADD COLUMN IF NOT EXISTS object_summary JSONB,
ADD COLUMN IF NOT EXISTS draft_status VARCHAR(30),
ADD COLUMN IF NOT EXISTS current_version_no INTEGER DEFAULT 1;

CREATE TABLE IF NOT EXISTS ai_solution_task_version (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    version_no      INTEGER NOT NULL,
    title           VARCHAR(300),
    result_content  TEXT,
    quality_result  JSONB,
    map_object      JSONB,
    object_summary  JSONB,
    source_snapshot JSONB,
    change_note     VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_origin
ON ai_solution_task(tenant_id, origin_type, object_type, object_id);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_draft_status
ON ai_solution_task(tenant_id, draft_status, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_task_version_no
ON ai_solution_task_version(tenant_id, task_id, version_no);
