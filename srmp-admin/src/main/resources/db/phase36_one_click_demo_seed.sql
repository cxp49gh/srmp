CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO tenant(id, tenant_code, tenant_name, tenant_type, status, created_at, updated_at, deleted)
VALUES ('default', 'default', '默认租户', 'PLATFORM', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_code) DO UPDATE
SET tenant_name = EXCLUDED.tenant_name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH route_shape AS (
  SELECT ST_GeomFromText('LINESTRING(106.0043335 25.7825803,106.0427856 25.9247018,106.2254333 25.8678737,106.1293030 25.7405291)', 4326) AS geom
)
INSERT INTO road_route(id, tenant_id, route_code, route_name, route_type, admin_grade, technical_grade, start_stake, end_stake, length_km, geom, remark, created_at, updated_at, deleted)
SELECT 'demo-route-g210', 'default', 'G210', 'G210 演示路线', 'NATIONAL_HIGHWAY', 'NATIONAL', 'FIRST_CLASS', 0, 100, 100, geom, 'one-click demo seed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM route_shape
ON CONFLICT (tenant_id, route_code) DO UPDATE
SET route_name = EXCLUDED.route_name,
    route_type = EXCLUDED.route_type,
    admin_grade = EXCLUDED.admin_grade,
    technical_grade = EXCLUDED.technical_grade,
    start_stake = EXCLUDED.start_stake,
    end_stake = EXCLUDED.end_stake,
    length_km = EXCLUDED.length_km,
    geom = EXCLUDED.geom,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH route_row AS (
  SELECT id AS route_id, geom
  FROM road_route
  WHERE tenant_id = 'default' AND route_code = 'G210'
),
section_seed AS (
  SELECT gs AS section_no,
         (gs * 25)::numeric(12,3) AS start_stake,
         ((gs + 1) * 25)::numeric(12,3) AS end_stake,
         ST_MakeLine(
           ST_LineInterpolatePoint(route_row.geom, gs / 4.0),
           ST_LineInterpolatePoint(route_row.geom, (gs + 1) / 4.0)
         ) AS geom,
         route_row.route_id
  FROM route_row
  CROSS JOIN generate_series(0, 3) AS gs
)
INSERT INTO road_section(id, tenant_id, route_id, route_code, section_code, section_name, direction, start_stake, end_stake, length_km, pavement_type, technical_grade, geom, created_at, updated_at, deleted)
SELECT 'demo-section-g210-' || section_no,
       'default',
       route_id,
       'G210',
       'G210_K' || start_stake::int || '_K' || end_stake::int,
       'G210 K' || start_stake::int || '-K' || end_stake::int || ' 演示路段',
       'BOTH',
       start_stake,
       end_stake,
       25,
       'ASPHALT',
       'FIRST_CLASS',
       geom,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM section_seed
ON CONFLICT (tenant_id, section_code) DO UPDATE
SET section_name = EXCLUDED.section_name,
    start_stake = EXCLUDED.start_stake,
    end_stake = EXCLUDED.end_stake,
    length_km = EXCLUDED.length_km,
    pavement_type = EXCLUDED.pavement_type,
    technical_grade = EXCLUDED.technical_grade,
    geom = EXCLUDED.geom,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH route_row AS (
  SELECT id AS route_id, geom
  FROM road_route
  WHERE tenant_id = 'default' AND route_code = 'G210'
),
unit_seed AS (
  SELECT gs AS unit_no,
         (gs * 0.1)::numeric(12,3) AS start_stake,
         ((gs + 1) * 0.1)::numeric(12,3) AS end_stake,
         floor(gs / 250)::int AS section_no,
         ST_MakeLine(
           ST_LineInterpolatePoint(route_row.geom, gs / 1000.0),
           ST_LineInterpolatePoint(route_row.geom, (gs + 1) / 1000.0)
         ) AS geom,
         route_row.route_id
  FROM route_row
  CROSS JOIN generate_series(0, 999) AS gs
)
INSERT INTO road_evaluation_unit(id, tenant_id, route_id, section_id, route_code, unit_code, direction, lane_no, start_stake, end_stake, length_m, geom, center_point, pavement_type, technical_grade, road_width, created_at, updated_at, deleted)
SELECT 'demo-unit-g210-' || lpad(unit_no::text, 4, '0'),
       'default',
       unit_seed.route_id,
       section.id,
       'G210',
       'G210_BOTH_U' || lpad(unit_no::text, 4, '0'),
       'BOTH',
       1,
       unit_seed.start_stake,
       unit_seed.end_stake,
       100,
       unit_seed.geom,
       ST_LineInterpolatePoint(unit_seed.geom, 0.5),
       'ASPHALT',
       'FIRST_CLASS',
       12.00,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM unit_seed
JOIN road_section section
  ON section.tenant_id = 'default'
 AND section.section_code = 'G210_K' || (unit_seed.section_no * 25) || '_K' || ((unit_seed.section_no + 1) * 25)
ON CONFLICT (tenant_id, unit_code) DO UPDATE
SET start_stake = EXCLUDED.start_stake,
    end_stake = EXCLUDED.end_stake,
    length_m = EXCLUDED.length_m,
    geom = EXCLUDED.geom,
    center_point = EXCLUDED.center_point,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH units AS (
  SELECT id, route_id, section_id, route_code, direction, start_stake, end_stake,
         substring(unit_code from '[0-9]{4}$')::int AS unit_no
  FROM road_evaluation_unit
  WHERE tenant_id = 'default'
    AND route_code = 'G210'
    AND unit_code ~ '^G210_BOTH_U[0-9]{4}$'
    AND deleted = FALSE
)
INSERT INTO assessment_result(id, tenant_id, object_type, object_id, route_id, section_id, unit_id, route_code, direction, start_stake, end_stake, year, mqi, sci, pqi, bci, tci, pci, rqi, rdi, pbi, pwi, sri, pssi, grade, assessed_at, created_at, updated_at, deleted)
SELECT 'demo-assess-g210-' || lpad(unit_no::text, 4, '0'),
       'default',
       'EVALUATION_UNIT',
       id,
       route_id,
       section_id,
       id,
       route_code,
       direction,
       start_stake,
       end_stake,
       2026,
       (72 + (unit_no % 27))::numeric,
       (76 + (unit_no % 19))::numeric,
       (70 + (unit_no % 25))::numeric,
       (78 + (unit_no % 17))::numeric,
       (74 + (unit_no % 21))::numeric,
       (68 + (unit_no % 29))::numeric,
       (73 + (unit_no % 22))::numeric,
       (71 + (unit_no % 24))::numeric,
       (75 + (unit_no % 20))::numeric,
       (72 + (unit_no % 23))::numeric,
       (70 + (unit_no % 26))::numeric,
       (69 + (unit_no % 28))::numeric,
       CASE
         WHEN unit_no % 100 = 0 THEN 'BAD'
         WHEN unit_no % 17 = 0 THEN 'POOR'
         WHEN unit_no % 5 = 0 THEN 'MEDIUM'
         WHEN unit_no % 3 = 0 THEN 'GOOD'
         ELSE 'EXCELLENT'
       END,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM units
ON CONFLICT (id) DO UPDATE
SET mqi = EXCLUDED.mqi,
    pqi = EXCLUDED.pqi,
    pci = EXCLUDED.pci,
    grade = EXCLUDED.grade,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH units AS (
  SELECT id, route_id, section_id, route_code, direction, start_stake, end_stake, center_point,
         substring(unit_code from '[0-9]{4}$')::int AS unit_no
  FROM road_evaluation_unit
  WHERE tenant_id = 'default'
    AND route_code = 'G210'
    AND unit_code ~ '^G210_BOTH_U[0-9]{4}$'
    AND deleted = FALSE
),
disease_seed AS (
  SELECT units.*, slot,
         CASE slot WHEN 1 THEN 'CRACK' WHEN 2 THEN 'POTHOLE' ELSE 'SUBSIDENCE' END AS disease_type,
         CASE slot WHEN 1 THEN '裂缝' WHEN 2 THEN '坑槽' ELSE '沉陷' END AS disease_name,
         CASE
           WHEN units.unit_no % 11 = 0 THEN 'HEAVY'
           WHEN units.unit_no % 3 = 0 THEN 'MEDIUM'
           ELSE 'LIGHT'
         END AS severity
  FROM units
  CROSS JOIN generate_series(1, 3) AS slot
)
INSERT INTO disease_record(id, tenant_id, route_id, section_id, unit_id, route_code, direction, lane_no, start_stake, end_stake, disease_category, disease_type, disease_name, severity, quantity, measure_unit, damage_area, damage_length, source, confidence, geom, status, verified, created_at, updated_at, deleted)
SELECT 'demo-disease-g210-' || lpad(unit_no::text, 4, '0') || '-' || slot,
       'default',
       route_id,
       section_id,
       id,
       route_code,
       direction,
       1,
       start_stake,
       end_stake,
       'PAVEMENT',
       disease_type,
       disease_name,
       severity,
       CASE slot WHEN 1 THEN 8 + (unit_no % 40) WHEN 2 THEN 1 + (unit_no % 4) ELSE 2 + (unit_no % 9) END,
       CASE slot WHEN 2 THEN '处' ELSE 'm' END,
       CASE slot WHEN 2 THEN 1.5 + (unit_no % 7) ELSE NULL END,
       CASE slot WHEN 2 THEN NULL ELSE 6 + (unit_no % 80) END,
       'DEMO_DATA',
       0.9500,
       center_point,
       'VERIFIED',
       TRUE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM disease_seed
ON CONFLICT (id) DO UPDATE
SET severity = EXCLUDED.severity,
    quantity = EXCLUDED.quantity,
    geom = EXCLUDED.geom,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO index_result(id, tenant_id, assessment_id, unit_id, route_id, section_id, route_code, direction, start_stake, end_stake, year, index_code, index_name, index_value, grade, created_at, updated_at, deleted)
SELECT 'demo-index-mqi-' || unit_id,
       tenant_id,
       id,
       unit_id,
       route_id,
       section_id,
       route_code,
       direction,
       start_stake,
       end_stake,
       year,
       'MQI',
       '公路技术状况指数',
       mqi,
       grade,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM assessment_result
WHERE tenant_id = 'default'
  AND route_code = 'G210'
  AND year = 2026
  AND unit_id IN (
      SELECT id
      FROM road_evaluation_unit
      WHERE tenant_id = 'default'
        AND route_code = 'G210'
        AND unit_code ~ '^G210_BOTH_U[0-9]{4}$'
  )
ON CONFLICT (id) DO UPDATE
SET index_value = EXCLUDED.index_value,
    grade = EXCLUDED.grade,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;
