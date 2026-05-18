-- AI 知识来源反馈（知识缺失 / 来源不准确）
CREATE TABLE IF NOT EXISTS ai_knowledge_feedback (
    id               VARCHAR(64) PRIMARY KEY,
    tenant_id        VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64),
    feedback_type    VARCHAR(32) NOT NULL,
    question         TEXT,
    remark           TEXT,
    business_context JSONB DEFAULT '{}'::jsonb,
    cited_sources    JSONB DEFAULT '[]'::jsonb,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_feedback_tenant
    ON ai_knowledge_feedback (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_feedback_type
    ON ai_knowledge_feedback (tenant_id, feedback_type, created_at DESC);
