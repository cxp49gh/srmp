-- 智路养护平台一期演示数据
-- 适用阶段：阶段六 一期演示闭环与验收
-- 执行前请确保已执行 schema.sql、init_dict.sql、init_admin.sql
--
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/demo_data.sql

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. 默认租户、组织、用户兜底
INSERT INTO tenant(id, tenant_code, tenant_name, tenant_type, status, created_at, updated_at, deleted)
VALUES ('default', 'default', '默认租户', 'PLATFORM', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_code) DO NOTHING;

INSERT INTO sys_org(id, tenant_id, org_code, org_name, org_type, adcode, enabled, created_at, updated_at, deleted)
VALUES ('demo-org-root', 'default', 'ROOT', '默认组织', 'BUREAU', '520000', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_id, org_code) DO NOTHING;

INSERT INTO sys_user(id, tenant_id, org_id, username, password, real_name, status, created_at, updated_at, deleted)
VALUES ('demo-user-admin', 'default', 'demo-org-root', 'admin', 'admin123', '系统管理员', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_id, username) DO NOTHING;

-- 2. 清理旧演示数据，便于重复执行
DELETE FROM index_result WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM assessment_result WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM disease_record WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM inspection_track WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM inspection_task WHERE tenant_id = 'default' AND task_code = 'DEMO_INSPECTION_G210_2026';
DELETE FROM road_evaluation_unit WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_section WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_route WHERE tenant_id = 'default' AND route_code = 'G210';

-- 3. 病害类型字典
INSERT INTO disease_type_dict(
    id, tenant_id, disease_code, disease_name, disease_category, measure_unit, related_index,
    severity_enabled, enabled, sort_no, remark, created_at, updated_at, deleted
)
VALUES
('demo-disease-type-crack', 'default', 'CRACK', '裂缝', 'PAVEMENT', 'm', 'PCI', TRUE, TRUE, 1, '演示病害类型', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('demo-disease-type-pothole', 'default', 'POTHOLE', '坑槽', 'PAVEMENT', 'm2', 'PCI', TRUE, TRUE, 2, '演示病害类型', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('demo-disease-type-rutting', 'default', 'RUTTING', '车辙', 'PAVEMENT', 'm', 'RDI', TRUE, TRUE, 3, '演示病害类型', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('demo-disease-type-subsidence', 'default', 'SUBSIDENCE', '沉陷', 'PAVEMENT', 'm2', 'PCI', TRUE, TRUE, 4, '演示病害类型', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_id, disease_code) DO UPDATE
SET disease_name = EXCLUDED.disease_name,
    disease_category = EXCLUDED.disease_category,
    measure_unit = EXCLUDED.measure_unit,
    related_index = EXCLUDED.related_index,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

-- 4. 示例路线 G210
INSERT INTO road_route(
    id, tenant_id, route_code, route_name, route_type, admin_grade, technical_grade,
    start_stake, end_stake, length_km, adcode, manage_org_id, geom,
    remark, created_by, updated_by, created_at, updated_at, deleted
)
VALUES (
    'demo-route-g210',
    'default',
    'G210',
    'G210 演示路线',
    'NATIONAL_HIGHWAY',
    'NATIONAL',
    'FIRST_CLASS',
    0.000,
    10.000,
    10.000,
    '520100',
    'demo-org-root',
    ST_GeomFromText('LINESTRING(106.630 26.650,106.640 26.660,106.650 26.668,106.662 26.674,106.675 26.680,106.690 26.688,106.705 26.696,106.720 26.710)', 4326),
    '阶段六演示路线',
    'demo',
    'demo',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- 5. 示例路段
INSERT INTO road_section(
    id, tenant_id, route_id, route_code, section_code, section_name, direction,
    start_stake, end_stake, length_km, pavement_type, technical_grade, lane_count,
    road_width, traffic_volume_level, adcode, manage_org_id, geom,
    remark, created_by, updated_by, created_at, updated_at, deleted
)
VALUES (
    'demo-section-g210-k0-k10',
    'default',
    'demo-route-g210',
    'G210',
    'G210_K0_K10',
    'G210 K0+000 - K10+000 演示路段',
    'BOTH',
    0.000,
    10.000,
    10.000,
    'ASPHALT',
    'FIRST_CLASS',
    4,
    18.00,
    'MEDIUM',
    '520100',
    'demo-org-root',
    ST_GeomFromText('LINESTRING(106.630 26.650,106.640 26.660,106.650 26.668,106.662 26.674,106.675 26.680,106.690 26.688,106.705 26.696,106.720 26.710)', 4326),
    '阶段六演示路段',
    'demo',
    'demo',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- 6. 10 个评定单元
INSERT INTO road_evaluation_unit(
    id, tenant_id, route_id, section_id, route_code, unit_code, direction, lane_no,
    start_stake, end_stake, length_m, pavement_type, technical_grade, road_width,
    adcode, manage_org_id, geom, center_point, created_by, updated_by, created_at, updated_at, deleted
)
VALUES
('demo-unit-g210-k0-k1','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K0_K1','BOTH',1,0.000,1.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.630 26.650,106.640 26.660)',4326),ST_GeomFromText('POINT(106.635 26.655)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k1-k2','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K1_K2','BOTH',1,1.000,2.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.640 26.660,106.650 26.668)',4326),ST_GeomFromText('POINT(106.645 26.664)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k2-k3','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K2_K3','BOTH',1,2.000,3.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.650 26.668,106.662 26.674)',4326),ST_GeomFromText('POINT(106.656 26.671)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k3-k4','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K3_K4','BOTH',1,3.000,4.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.662 26.674,106.675 26.680)',4326),ST_GeomFromText('POINT(106.6685 26.677)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k4-k5','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K4_K5','BOTH',1,4.000,5.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.675 26.680,106.690 26.688)',4326),ST_GeomFromText('POINT(106.6825 26.684)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k5-k6','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K5_K6','BOTH',1,5.000,6.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.690 26.688,106.700 26.693)',4326),ST_GeomFromText('POINT(106.695 26.6905)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k6-k7','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K6_K7','BOTH',1,6.000,7.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.700 26.693,106.705 26.696)',4326),ST_GeomFromText('POINT(106.7025 26.6945)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k7-k8','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K7_K8','BOTH',1,7.000,8.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.705 26.696,106.710 26.700)',4326),ST_GeomFromText('POINT(106.7075 26.698)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k8-k9','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K8_K9','BOTH',1,8.000,9.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.710 26.700,106.715 26.705)',4326),ST_GeomFromText('POINT(106.7125 26.7025)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k9-k10','default','demo-route-g210','demo-section-g210-k0-k10','G210','G210_BOTH_K9_K10','BOTH',1,9.000,10.000,1000,'ASPHALT','FIRST_CLASS',18.00,'520100','demo-org-root',ST_GeomFromText('LINESTRING(106.715 26.705,106.720 26.710)',4326),ST_GeomFromText('POINT(106.7175 26.7075)',4326),'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

-- 7. 巡检任务与轨迹
INSERT INTO inspection_task(
    id, tenant_id, task_code, task_name, task_type, route_id, route_code,
    start_stake, end_stake, year, inspect_date, inspect_method, data_source,
    status, remark, created_by, updated_by, created_at, updated_at, deleted
)
VALUES (
    'demo-task-g210-2026',
    'default',
    'DEMO_INSPECTION_G210_2026',
    'G210 2026 年演示巡检任务',
    'ANNUAL_ASSESSMENT',
    'demo-route-g210',
    'G210',
    0.000,
    10.000,
    2026,
    DATE '2026-04-01',
    'IMPORT',
    'DEMO_DATA',
    'ASSESSED',
    '阶段六演示巡检任务',
    'demo',
    'demo',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

INSERT INTO inspection_track(
    id, tenant_id, task_id, route_code, track_name, start_time, end_time, mileage_km,
    geom, raw_file_id, created_at, updated_at, deleted
)
VALUES (
    'demo-track-g210-2026',
    'default',
    'demo-task-g210-2026',
    'G210',
    'G210 2026 年演示巡检轨迹',
    TIMESTAMP '2026-04-01 09:00:00',
    TIMESTAMP '2026-04-01 10:30:00',
    10.000,
    ST_GeomFromText('LINESTRING(106.630 26.650,106.640 26.660,106.650 26.668,106.662 26.674,106.675 26.680,106.690 26.688,106.705 26.696,106.720 26.710)',4326),
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);

-- 8. 病害记录
INSERT INTO disease_record(
    id, tenant_id, task_id, route_id, section_id, unit_id, route_code, direction, lane_no,
    start_stake, end_stake, disease_category, disease_type, disease_name, severity,
    quantity, measure_unit, damage_area, damage_length, damage_width, damage_depth,
    source, confidence, geom, status, verified, verified_by, verified_at,
    remark, created_by, updated_by, created_at, updated_at, deleted
)
VALUES
('demo-disease-001','default','demo-task-g210-2026','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k1-k2','G210','BOTH',1,1.200,1.250,'PAVEMENT','CRACK','裂缝','LIGHT',35.0,'m',NULL,35.0,0.03,NULL,'DEMO_DATA',0.9800,ST_GeomFromText('LINESTRING(106.642 26.661,106.644 26.663)',4326),'VERIFIED',TRUE,'demo',CURRENT_TIMESTAMP,'演示裂缝','demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-002','default','demo-task-g210-2026','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k2-k3','G210','BOTH',2,2.400,2.405,'PAVEMENT','POTHOLE','坑槽','HEAVY',1.0,'处',3.2,1.8,1.6,0.08,'DEMO_DATA',0.9500,ST_GeomFromText('POINT(106.655 26.670)',4326),'VERIFIED',TRUE,'demo',CURRENT_TIMESTAMP,'演示坑槽','demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-003','default','demo-task-g210-2026','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k3-k4','G210','BOTH',1,3.500,3.900,'PAVEMENT','RUTTING','车辙','MEDIUM',120.0,'m',NULL,120.0,0.8,NULL,'DEMO_DATA',0.9200,ST_GeomFromText('LINESTRING(106.666 26.676,106.672 26.678)',4326),'VERIFIED',TRUE,'demo',CURRENT_TIMESTAMP,'演示车辙','demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-004','default','demo-task-g210-2026','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k4-k5','G210','BOTH',2,4.300,4.380,'PAVEMENT','SUBSIDENCE','沉陷','HEAVY',1.0,'处',8.5,4.0,2.1,0.10,'DEMO_DATA',0.9400,ST_GeomFromText('POLYGON((106.681 26.683,106.684 26.684,106.685 26.686,106.682 26.685,106.681 26.683))',4326),'VERIFIED',TRUE,'demo',CURRENT_TIMESTAMP,'演示沉陷','demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-005','default','demo-task-g210-2026','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k6-k7','G210','BOTH',1,6.200,6.260,'PAVEMENT','POTHOLE','坑槽','MEDIUM',2.0,'处',2.4,1.6,1.2,0.05,'DEMO_DATA',0.9300,ST_GeomFromText('POINT(106.702 26.694)',4326),'VERIFIED',TRUE,'demo',CURRENT_TIMESTAMP,'演示坑槽','demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-006','default','demo-task-g210-2026','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k8-k9','G210','BOTH',1,8.100,8.400,'PAVEMENT','CRACK','裂缝','MEDIUM',80.0,'m',NULL,80.0,0.04,NULL,'DEMO_DATA',0.9100,ST_GeomFromText('LINESTRING(106.711 26.701,106.713 26.703)',4326),'VERIFIED',TRUE,'demo',CURRENT_TIMESTAMP,'演示裂缝','demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

-- 9. 综合评定结果
INSERT INTO assessment_result(
    id, tenant_id, task_id, object_type, object_id, route_id, section_id, unit_id,
    route_code, direction, start_stake, end_stake, year, standard_code,
    mqi, sci, pqi, bci, tci, pci, rqi, rdi, pbi, pwi, sri, pssi,
    grade, zero_reason, assessed_at, created_by, updated_by, created_at, updated_at, deleted
)
VALUES
('demo-assess-g210-k0-k1','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k0-k1','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k0-k1','G210','BOTH',0.000,1.000,2026,'JTG_5210_2018',92,95,91,90,93,92,91,90,93,94,91,90,'EXCELLENT',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k1-k2','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k1-k2','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k1-k2','G210','BOTH',1.000,2.000,2026,'JTG_5210_2018',84,88,83,87,86,82,85,84,83,85,82,83,'GOOD',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k2-k3','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k2-k3','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k2-k3','G210','BOTH',2.000,3.000,2026,'JTG_5210_2018',66,78,64,82,80,58,75,70,65,68,66,65,'POOR',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k3-k4','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k3-k4','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k3-k4','G210','BOTH',3.000,4.000,2026,'JTG_5210_2018',76,84,74,85,82,72,78,65,75,76,74,74,'MEDIUM',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k4-k5','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k4-k5','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k4-k5','G210','BOTH',4.000,5.000,2026,'JTG_5210_2018',55,70,53,80,78,48,66,60,58,60,55,54,'BAD',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k5-k6','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k5-k6','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k5-k6','G210','BOTH',5.000,6.000,2026,'JTG_5210_2018',88,90,87,88,89,86,88,86,88,89,87,86,'GOOD',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k6-k7','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k6-k7','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k6-k7','G210','BOTH',6.000,7.000,2026,'JTG_5210_2018',79,86,78,86,84,75,80,76,78,79,77,78,'MEDIUM',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k7-k8','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k7-k8','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k7-k8','G210','BOTH',7.000,8.000,2026,'JTG_5210_2018',90,92,90,91,92,91,90,90,91,92,90,90,'EXCELLENT',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k8-k9','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k8-k9','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k8-k9','G210','BOTH',8.000,9.000,2026,'JTG_5210_2018',82,87,80,87,85,78,82,80,81,82,80,79,'GOOD',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k9-k10','default','demo-task-g210-2026','EVALUATION_UNIT','demo-unit-g210-k9-k10','demo-route-g210','demo-section-g210-k0-k10','demo-unit-g210-k9-k10','G210','BOTH',9.000,10.000,2026,'JTG_5210_2018',86,88,85,88,87,84,86,85,86,86,85,84,'GOOD',NULL,CURRENT_TIMESTAMP,'demo','demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

-- 10. 指标结果：为每个评定单元生成 MQI/PQI/PCI/RQI/RDI 记录
INSERT INTO index_result(
    id, tenant_id, task_id, assessment_id, unit_id, route_id, section_id, route_code, direction,
    start_stake, end_stake, year, index_code, index_name, index_value, grade, raw_metrics,
    calculation_version, calculated_at, created_at, updated_at, deleted
)
SELECT
    'demo-index-' || lower(a.index_code) || '-' || ar.unit_id,
    ar.tenant_id,
    ar.task_id,
    ar.id,
    ar.unit_id,
    ar.route_id,
    ar.section_id,
    ar.route_code,
    ar.direction,
    ar.start_stake,
    ar.end_stake,
    ar.year,
    a.index_code,
    a.index_name,
    a.index_value,
    ar.grade,
    jsonb_build_object('source', 'demo_data', 'unitId', ar.unit_id),
    'DEMO_V1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
FROM assessment_result ar
CROSS JOIN LATERAL (
    VALUES
    ('MQI', '公路技术状况指数', ar.mqi),
    ('PQI', '路面技术状况指数', ar.pqi),
    ('PCI', '路面损坏状况指数', ar.pci),
    ('RQI', '路面行驶质量指数', ar.rqi),
    ('RDI', '路面车辙深度指数', ar.rdi)
) AS a(index_code, index_name, index_value)
WHERE ar.tenant_id = 'default'
  AND ar.route_code = 'G210'
  AND ar.deleted = FALSE;

-- 11. 简要统计提示
SELECT
    'demo_data_loaded' AS status,
    (SELECT count(*) FROM road_route WHERE tenant_id='default' AND route_code='G210') AS route_count,
    (SELECT count(*) FROM road_evaluation_unit WHERE tenant_id='default' AND route_code='G210') AS unit_count,
    (SELECT count(*) FROM disease_record WHERE tenant_id='default' AND route_code='G210') AS disease_count,
    (SELECT count(*) FROM assessment_result WHERE tenant_id='default' AND route_code='G210') AS assessment_count;
