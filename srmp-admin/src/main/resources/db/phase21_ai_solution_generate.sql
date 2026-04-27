-- 阶段二十一：AI 方案生成
-- 执行：
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql

CREATE TABLE IF NOT EXISTS ai_solution_task (
    id               VARCHAR(64) PRIMARY KEY,
    tenant_id        VARCHAR(64) NOT NULL,
    solution_type    VARCHAR(100) NOT NULL,
    title            VARCHAR(300),
    route_code       VARCHAR(50),
    year             INTEGER,
    template_id      VARCHAR(64),
    template_version VARCHAR(50),
    status           VARCHAR(30),
    request_json     JSONB,
    result_content   TEXT,
    quality_result   JSONB,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_solution_source (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    source_type     VARCHAR(50),
    source_title    VARCHAR(300),
    source_id       VARCHAR(200),
    source_url      VARCHAR(1000),
    content_excerpt TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_tenant
ON ai_solution_task(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_route
ON ai_solution_task(tenant_id, route_code, year, solution_type);

CREATE INDEX IF NOT EXISTS idx_ai_solution_source_task
ON ai_solution_source(tenant_id, task_id);
