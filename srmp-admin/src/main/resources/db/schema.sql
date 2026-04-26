CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_code     VARCHAR(100) NOT NULL,
    tenant_name     VARCHAR(200) NOT NULL,
    tenant_type     VARCHAR(50),
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(50),
    status          VARCHAR(30) DEFAULT 'ENABLED',
    remark          TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_tenant_code UNIQUE(tenant_code)
);

CREATE TABLE IF NOT EXISTS sys_org (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    org_code        VARCHAR(100) NOT NULL,
    org_name        VARCHAR(200) NOT NULL,
    parent_id       VARCHAR(64),
    org_type        VARCHAR(50),
    adcode          VARCHAR(20),
    sort_no         INTEGER DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_sys_org_code UNIQUE(tenant_id, org_code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    org_id          VARCHAR(64),
    username        VARCHAR(100) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(100),
    phone           VARCHAR(50),
    email           VARCHAR(200),
    status          VARCHAR(30) DEFAULT 'ENABLED',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_sys_user_username UNIQUE(tenant_id, username)
);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    dict_type       VARCHAR(100) NOT NULL,
    dict_name       VARCHAR(200) NOT NULL,
    description     TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_dict_type UNIQUE(tenant_id, dict_type)
);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    dict_type       VARCHAR(100) NOT NULL,
    item_code       VARCHAR(100) NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    item_value      VARCHAR(200),
    sort_no         INTEGER DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    remark          TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_dict_item UNIQUE(tenant_id, dict_type, item_code)
);

CREATE TABLE IF NOT EXISTS road_route (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    route_code          VARCHAR(50) NOT NULL,
    route_name          VARCHAR(200) NOT NULL,
    route_type          VARCHAR(50) NOT NULL,
    admin_grade         VARCHAR(50),
    technical_grade     VARCHAR(50),
    start_stake         NUMERIC(12,3),
    end_stake           NUMERIC(12,3),
    length_km           NUMERIC(12,3),
    adcode              VARCHAR(20),
    manage_org_id       VARCHAR(64),
    geom                GEOMETRY(LineString, 4326),
    remark              TEXT,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_route_code UNIQUE(tenant_id, route_code)
);

CREATE INDEX IF NOT EXISTS idx_route_geom ON road_route USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_route_code ON road_route(tenant_id, route_code);

CREATE TABLE IF NOT EXISTS road_section (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64) NOT NULL,
    route_code            VARCHAR(50) NOT NULL,
    section_code          VARCHAR(100) NOT NULL,
    section_name          VARCHAR(200),
    direction             VARCHAR(20) DEFAULT 'BOTH',
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_km             NUMERIC(12,3),
    pavement_type         VARCHAR(50),
    technical_grade       VARCHAR(50),
    geom                  GEOMETRY(LineString, 4326),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_section_code UNIQUE(tenant_id, section_code)
);

CREATE INDEX IF NOT EXISTS idx_section_geom ON road_section USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_section_stake ON road_section(tenant_id, route_code, direction, start_stake, end_stake);

CREATE TABLE IF NOT EXISTS road_evaluation_unit (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64) NOT NULL,
    section_id            VARCHAR(64),
    route_code            VARCHAR(50) NOT NULL,
    unit_code             VARCHAR(100) NOT NULL,
    direction             VARCHAR(20) DEFAULT 'BOTH',
    lane_no               INTEGER,
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_m              INTEGER DEFAULT 1000,
    geom                  GEOMETRY(LineString, 4326),
    center_point          GEOMETRY(Point, 4326),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_eval_unit_code UNIQUE(tenant_id, unit_code)
);

CREATE INDEX IF NOT EXISTS idx_eval_unit_geom ON road_evaluation_unit USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_eval_unit_center ON road_evaluation_unit USING GIST(center_point);
CREATE INDEX IF NOT EXISTS idx_eval_unit_route_stake ON road_evaluation_unit(tenant_id, route_code, direction, start_stake, end_stake);


-- 阶段二道路资产补充字段，重复执行安全。
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS lane_count INTEGER;
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS road_width NUMERIC(8,2);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS traffic_volume_level VARCHAR(50);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS adcode VARCHAR(20);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS manage_org_id VARCHAR(64);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS pavement_type VARCHAR(50);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS technical_grade VARCHAR(50);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS road_width NUMERIC(8,2);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS adcode VARCHAR(20);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS manage_org_id VARCHAR(64);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);
