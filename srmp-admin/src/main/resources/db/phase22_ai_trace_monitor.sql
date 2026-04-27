-- 阶段二十二：AI 调用链路监控与超时治理
CREATE TABLE IF NOT EXISTS ai_trace_log (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(100) NOT NULL,
    request_type    VARCHAR(50),
    user_message    TEXT,
    mode            VARCHAR(50),
    status          VARCHAR(30),
    total_cost_ms   INTEGER,
    fallback        BOOLEAN DEFAULT FALSE,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ai_trace_step (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(100) NOT NULL,
    step_name       VARCHAR(100),
    step_label      VARCHAR(100),
    status          VARCHAR(30),
    cost_ms         INTEGER,
    hit_count       INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_trace_log_tenant_time ON ai_trace_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_trace_log_trace_id ON ai_trace_log(tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_log_status ON ai_trace_log(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_trace_id ON ai_trace_step(tenant_id, trace_id, created_at ASC);
