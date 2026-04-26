CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO tenant(id, tenant_code, tenant_name, tenant_type, status, created_at, updated_at, deleted)
VALUES ('default', 'default', '默认租户', 'PLATFORM', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_code) DO NOTHING;

DELETE FROM index_result WHERE tenant_id='default' AND route_code='G210';
DELETE FROM assessment_result WHERE tenant_id='default' AND route_code='G210';
DELETE FROM disease_record WHERE tenant_id='default' AND route_code='G210';
DELETE FROM road_evaluation_unit WHERE tenant_id='default' AND route_code='G210';
DELETE FROM road_section WHERE tenant_id='default' AND route_code='G210';
DELETE FROM road_route WHERE tenant_id='default' AND route_code='G210';

INSERT INTO road_route(id,tenant_id,route_code,route_name,route_type,technical_grade,start_stake,end_stake,length_km,geom,created_at,updated_at,deleted)
VALUES('demo-route-g210','default','G210','G210 演示路线','NATIONAL_HIGHWAY','FIRST_CLASS',0,3,3,ST_GeomFromText('LINESTRING(106.630 26.650,106.650 26.668,106.675 26.680,106.720 26.710)',4326),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

INSERT INTO road_section(id,tenant_id,route_id,route_code,section_code,section_name,direction,start_stake,end_stake,length_km,pavement_type,technical_grade,geom,created_at,updated_at,deleted)
VALUES('demo-section-g210-k0-k3','default','demo-route-g210','G210','G210_K0_K3','G210 K0-K3 演示路段','BOTH',0,3,3,'ASPHALT','FIRST_CLASS',ST_GeomFromText('LINESTRING(106.630 26.650,106.650 26.668,106.675 26.680,106.720 26.710)',4326),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

INSERT INTO road_evaluation_unit(id,tenant_id,route_id,section_id,route_code,unit_code,direction,lane_no,start_stake,end_stake,length_m,geom,center_point,created_at,updated_at,deleted)
VALUES
('demo-unit-g210-k0-k1','default','demo-route-g210','demo-section-g210-k0-k3','G210','G210_BOTH_K0_K1','BOTH',1,0,1,1000,ST_GeomFromText('LINESTRING(106.630 26.650,106.650 26.668)',4326),ST_GeomFromText('POINT(106.640 26.659)',4326),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k1-k2','default','demo-route-g210','demo-section-g210-k0-k3','G210','G210_BOTH_K1_K2','BOTH',1,1,2,1000,ST_GeomFromText('LINESTRING(106.650 26.668,106.675 26.680)',4326),ST_GeomFromText('POINT(106.662 26.674)',4326),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-unit-g210-k2-k3','default','demo-route-g210','demo-section-g210-k0-k3','G210','G210_BOTH_K2_K3','BOTH',1,2,3,1000,ST_GeomFromText('LINESTRING(106.675 26.680,106.720 26.710)',4326),ST_GeomFromText('POINT(106.697 26.695)',4326),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

INSERT INTO disease_record(id,tenant_id,route_id,section_id,unit_id,route_code,direction,lane_no,start_stake,end_stake,disease_category,disease_type,disease_name,severity,quantity,measure_unit,damage_area,damage_length,source,confidence,geom,status,verified,created_at,updated_at,deleted)
VALUES
('demo-disease-001','default','demo-route-g210','demo-section-g210-k0-k3','demo-unit-g210-k0-k1','G210','BOTH',1,0.2,0.3,'PAVEMENT','CRACK','裂缝','LIGHT',30,'m',null,30,'DEMO_DATA',0.98,ST_GeomFromText('LINESTRING(106.635 26.654,106.640 26.659)',4326),'VERIFIED',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-002','default','demo-route-g210','demo-section-g210-k0-k3','demo-unit-g210-k1-k2','G210','BOTH',1,1.4,1.41,'PAVEMENT','POTHOLE','坑槽','HEAVY',1,'处',3.2,1.8,'DEMO_DATA',0.95,ST_GeomFromText('POINT(106.660 26.673)',4326),'VERIFIED',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-disease-003','default','demo-route-g210','demo-section-g210-k0-k3','demo-unit-g210-k2-k3','G210','BOTH',1,2.3,2.6,'PAVEMENT','RUTTING','车辙','MEDIUM',80,'m',null,80,'DEMO_DATA',0.93,ST_GeomFromText('LINESTRING(106.690 26.690,106.705 26.700)',4326),'VERIFIED',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

INSERT INTO assessment_result(id,tenant_id,object_type,object_id,route_id,section_id,unit_id,route_code,direction,start_stake,end_stake,year,mqi,sci,pqi,bci,tci,pci,rqi,rdi,pbi,pwi,sri,pssi,grade,assessed_at,created_at,updated_at,deleted)
VALUES
('demo-assess-g210-k0-k1','default','EVALUATION_UNIT','demo-unit-g210-k0-k1','demo-route-g210','demo-section-g210-k0-k3','demo-unit-g210-k0-k1','G210','BOTH',0,1,2026,92,95,91,90,93,92,91,90,93,94,91,90,'EXCELLENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k1-k2','default','EVALUATION_UNIT','demo-unit-g210-k1-k2','demo-route-g210','demo-section-g210-k0-k3','demo-unit-g210-k1-k2','G210','BOTH',1,2,2026,66,78,64,82,80,58,75,70,65,68,66,65,'POOR',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE),
('demo-assess-g210-k2-k3','default','EVALUATION_UNIT','demo-unit-g210-k2-k3','demo-route-g210','demo-section-g210-k0-k3','demo-unit-g210-k2-k3','G210','BOTH',2,3,2026,82,87,80,87,85,78,82,80,81,82,80,79,'GOOD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

INSERT INTO index_result(id,tenant_id,assessment_id,unit_id,route_id,section_id,route_code,direction,start_stake,end_stake,year,index_code,index_name,index_value,grade,created_at,updated_at,deleted)
SELECT 'demo-index-mqi-'||unit_id, tenant_id, id, unit_id, route_id, section_id, route_code, direction, start_stake, end_stake, year, 'MQI', '公路技术状况指数', mqi, grade, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM assessment_result WHERE tenant_id='default' AND route_code='G210';
