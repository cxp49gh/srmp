-- 数据管理增强：归档字段 + 操作审计表（PostgreSQL）
ALTER TABLE data_mgmt_project ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE;
ALTER TABLE data_mgmt_project ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
ALTER TABLE data_mgmt_project ADD COLUMN IF NOT EXISTS archived_by VARCHAR(64);

CREATE TABLE IF NOT EXISTS data_mgmt_audit_log (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    project_id      VARCHAR(64),
    project_name    VARCHAR(200),
    operation_type  VARCHAR(64) NOT NULL,
    operator        VARCHAR(64),
    operated_at     TIMESTAMP NOT NULL,
    result          VARCHAR(32) NOT NULL,
    reason          TEXT,
    snapshot_before TEXT,
    snapshot_after  TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_data_mgmt_audit_tenant_time ON data_mgmt_audit_log(tenant_id, operated_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_mgmt_audit_project ON data_mgmt_audit_log(tenant_id, project_id);
