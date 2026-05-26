-- AI execution observability foundation.
-- Keeps legacy ai_trace_* tables intact and adds a normalized execution model.

CREATE TABLE IF NOT EXISTS ai_execution_run (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(128) NOT NULL,
    request_type    VARCHAR(64),
    action          VARCHAR(64),
    intent          VARCHAR(64),
    mode            VARCHAR(64),
    provider        VARCHAR(64),
    model           VARCHAR(128),
    graph_name      VARCHAR(128),
    status          VARCHAR(32) NOT NULL,
    fallback        BOOLEAN DEFAULT FALSE,
    fallback_reason TEXT,
    error_message   TEXT,
    total_cost_ms   INTEGER,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    raw_request     JSONB DEFAULT '{}'::jsonb,
    raw_response    JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_execution_run_tenant_trace ON ai_execution_run(tenant_id, trace_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_run_time ON ai_execution_run(tenant_id, created_at DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_run_status ON ai_execution_run(tenant_id, status, created_at DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_run_action ON ai_execution_run(tenant_id, action, intent, created_at DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_run_fallback ON ai_execution_run(tenant_id, fallback, created_at DESC) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS ai_execution_scope (
    id                      VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    execution_id            VARCHAR(64) NOT NULL,
    tenant_id               VARCHAR(64) NOT NULL,
    trace_id                VARCHAR(128) NOT NULL,
    project_id              VARCHAR(64),
    route_code              VARCHAR(64),
    year                    INTEGER,
    section_tier            VARCHAR(32),
    context_scope           VARCHAR(32),
    object_type             VARCHAR(64),
    object_id               VARCHAR(128),
    assessment_object_type  VARCHAR(64),
    direction               VARCHAR(32),
    start_stake             NUMERIC(12,3),
    end_stake               NUMERIC(12,3),
    bbox                    JSONB DEFAULT 'null'::jsonb,
    geometry_type           VARCHAR(64),
    selected_layers         JSONB DEFAULT '[]'::jsonb,
    scope_warnings          JSONB DEFAULT '[]'::jsonb,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_execution_scope_trace ON ai_execution_scope(tenant_id, trace_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_scope_project_route ON ai_execution_scope(tenant_id, project_id, route_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_scope_object ON ai_execution_scope(tenant_id, object_type, object_id) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS ai_execution_step (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    execution_id    VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(128) NOT NULL,
    step_name       VARCHAR(128),
    step_label      VARCHAR(200),
    status          VARCHAR(32),
    cost_ms         INTEGER,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    error_message   TEXT,
    step_data       JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_execution_step_trace ON ai_execution_step(tenant_id, trace_id, created_at ASC) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS ai_execution_tool_call (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    execution_id    VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(128) NOT NULL,
    step_id         VARCHAR(64),
    tool_name       VARCHAR(128) NOT NULL,
    status          VARCHAR(32),
    cost_ms         INTEGER,
    total_count     INTEGER,
    returned_count  INTEGER,
    truncated       BOOLEAN DEFAULT FALSE,
    query_scope     JSONB DEFAULT '{}'::jsonb,
    result_summary  JSONB DEFAULT '{}'::jsonb,
    raw_args        JSONB DEFAULT '{}'::jsonb,
    raw_result      JSONB DEFAULT '{}'::jsonb,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_execution_tool_trace ON ai_execution_tool_call(tenant_id, trace_id, created_at ASC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_tool_name ON ai_execution_tool_call(tenant_id, tool_name, created_at DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_execution_tool_status ON ai_execution_tool_call(tenant_id, status, created_at DESC) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS ai_execution_evidence (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    execution_id    VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(128) NOT NULL,
    source_type     VARCHAR(64),
    source_name     VARCHAR(128),
    title           VARCHAR(500),
    route_code      VARCHAR(64),
    object_type     VARCHAR(64),
    object_id       VARCHAR(128),
    score           NUMERIC(12,6),
    payload         JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_execution_evidence_trace ON ai_execution_evidence(tenant_id, trace_id, source_type) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS ai_execution_answer (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    execution_id    VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(128) NOT NULL,
    used_model      VARCHAR(128),
    answer_text     TEXT,
    answer_meta     JSONB DEFAULT '{}'::jsonb,
    quality_flags   JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_execution_answer_trace ON ai_execution_answer(tenant_id, trace_id) WHERE deleted = false;
