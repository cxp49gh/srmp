-- 数据管理：项目主档、导入流水、路网/路段/病害表 project_id 冗余
-- 适用于已有库增量升级（幂等，可重复执行）。
-- 本地 Docker：docker exec -i srmp-postgres psql -U srmp -d srmp -f - < migration_202605_data_management.sql
-- 或：docker exec -i srmp-postgres psql -U srmp -d srmp < srmp-admin/src/main/resources/db/migration_202605_data_management.sql

CREATE TABLE IF NOT EXISTS data_mgmt_project (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    remark          TEXT,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_data_mgmt_project_tenant_name ON data_mgmt_project(tenant_id, name);

CREATE TABLE IF NOT EXISTS data_import_record (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    project_id      VARCHAR(64) NOT NULL,
    import_type     VARCHAR(32) NOT NULL,
    file_name       VARCHAR(500),
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     BIGINT,
    status          VARCHAR(20) NOT NULL,
    message         TEXT,
    result_summary  TEXT,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_data_import_record_project_time ON data_import_record(tenant_id, project_id, started_at DESC);

ALTER TABLE road_route ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
ALTER TABLE road_section_line ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
ALTER TABLE road_section_ledger ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
ALTER TABLE road_section_km ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
ALTER TABLE road_section_hm ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
ALTER TABLE disease_record ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_road_route_tenant_project ON road_route(tenant_id, project_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_road_section_line_tenant_project ON road_section_line(tenant_id, project_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_disease_record_tenant_project ON disease_record(tenant_id, project_id) WHERE deleted = false;

-- 路网未登记该路线编号时仍允许导入路段包（route_id 可空）。
-- 命中路网时 route_code 与 road_route.route_code 一致；路段 SHP 标准列为 linkCode（见 .feature/导入路段数据-需求说明.md §4.2）。
ALTER TABLE road_section_line ALTER COLUMN route_id DROP NOT NULL;
ALTER TABLE road_section_ledger ALTER COLUMN route_id DROP NOT NULL;
ALTER TABLE road_section_km ALTER COLUMN route_id DROP NOT NULL;
ALTER TABLE road_section_hm ALTER COLUMN route_id DROP NOT NULL;

-- 路网：唯一性仅约束「未软删」路线，避免因历史 deleted 行占位导致无法再导入同名 route_code
ALTER TABLE road_route DROP CONSTRAINT IF EXISTS uk_road_route_code;
DROP INDEX IF EXISTS uk_road_route_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_road_route_active_code ON road_route(tenant_id, route_code) WHERE COALESCE(deleted, false) = false;
