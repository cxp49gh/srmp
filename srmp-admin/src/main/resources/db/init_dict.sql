INSERT INTO tenant(tenant_code, tenant_name, tenant_type, status)
VALUES ('default', '默认租户', 'PLATFORM', 'ENABLED')
ON CONFLICT (tenant_code) DO NOTHING;

INSERT INTO sys_dict_type(tenant_id, dict_type, dict_name, description)
VALUES
('default', 'route_type', '路线类型', '公路路线类型'),
('default', 'technical_grade', '技术等级', '公路技术等级'),
('default', 'pavement_type', '路面类型', '路面结构类型'),
('default', 'assessment_grade', '评定等级', '技术状况评定等级')
ON CONFLICT (tenant_id, dict_type) DO NOTHING;

INSERT INTO sys_dict_item(tenant_id, dict_type, item_code, item_name, sort_no)
VALUES ('default', 'route_type', 'OTHER', '外部路网导入', 99)
ON CONFLICT (tenant_id, dict_type, item_code) DO NOTHING;

INSERT INTO sys_dict_type(tenant_id, dict_type, dict_name, description)
VALUES
('default', 'disease_category', '病害大类', '病害所属业务大类'),
('default', 'disease_severity', '病害严重程度', '轻中重'),
('default', 'road_index_code', '技术状况指标', 'MQI/PQI/PCI等'),
('default', 'disease_status', '病害状态', '病害处理状态')
ON CONFLICT (tenant_id, dict_type) DO NOTHING;

INSERT INTO sys_dict_item(tenant_id, dict_type, item_code, item_name, sort_no)
VALUES
('default', 'disease_category', 'PAVEMENT', '路面', 1),
('default', 'disease_category', 'SUBGRADE', '路基', 2),
('default', 'disease_category', 'BRIDGE_TUNNEL_CULVERT', '桥隧涵', 3),
('default', 'disease_category', 'TRAFFIC_FACILITY', '沿线设施', 4),
('default', 'disease_severity', 'LIGHT', '轻度', 1),
('default', 'disease_severity', 'MEDIUM', '中度', 2),
('default', 'disease_severity', 'HEAVY', '重度', 3),
('default', 'road_index_code', 'MQI', '公路技术状况指数', 1),
('default', 'road_index_code', 'PQI', '路面技术状况指数', 2),
('default', 'road_index_code', 'PCI', '路面损坏状况指数', 3),
('default', 'road_index_code', 'RQI', '路面行驶质量指数', 4),
('default', 'road_index_code', 'RDI', '路面车辙深度指数', 5),
('default', 'road_index_code', 'SCI', '路基技术状况指数', 6),
('default', 'road_index_code', 'BCI', '桥隧构造物技术状况指数', 7),
('default', 'road_index_code', 'TCI', '沿线设施技术状况指数', 8)
ON CONFLICT (tenant_id, dict_type, item_code) DO NOTHING;

INSERT INTO disease_type_dict(tenant_id, disease_code, disease_name, disease_category, measure_unit, related_index, sort_no)
VALUES
('default', 'LONGITUDINAL_CRACK', '纵向裂缝', 'PAVEMENT', 'm', 'PCI', 1),
('default', 'TRANSVERSE_CRACK', '横向裂缝', 'PAVEMENT', 'm', 'PCI', 2),
('default', 'POTHOLE', '坑槽', 'PAVEMENT', 'm2', 'PCI', 3),
('default', 'RUTTING', '车辙', 'PAVEMENT', 'm', 'RDI', 4),
('default', 'SETTLEMENT', '沉陷', 'PAVEMENT', 'm2', 'PCI', 5)
ON CONFLICT (tenant_id, disease_code) DO NOTHING;
