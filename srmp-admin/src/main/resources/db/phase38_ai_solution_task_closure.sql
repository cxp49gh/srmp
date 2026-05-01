-- Phase38: AI solution task closure.
ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS ai_trace_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS ai_answer TEXT,
ADD COLUMN IF NOT EXISTS ai_sources JSONB,
ADD COLUMN IF NOT EXISTS ai_tool_results JSONB,
ADD COLUMN IF NOT EXISTS ai_evidence JSONB,
ADD COLUMN IF NOT EXISTS ai_context JSONB,
ADD COLUMN IF NOT EXISTS generation_mode VARCHAR(50),
ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

ALTER TABLE ai_solution_task_version
ADD COLUMN IF NOT EXISTS ai_context JSONB;

CREATE TABLE IF NOT EXISTS ai_solution_task_status_log (
    id            VARCHAR(64) PRIMARY KEY,
    tenant_id     VARCHAR(64) NOT NULL,
    task_id       VARCHAR(64) NOT NULL,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30) NOT NULL,
    action        VARCHAR(50),
    operator      VARCHAR(100),
    note          VARCHAR(500),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_ai_trace
ON ai_solution_task(tenant_id, ai_trace_id);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_status_log_task
ON ai_solution_task_status_log(tenant_id, task_id, created_at DESC);
