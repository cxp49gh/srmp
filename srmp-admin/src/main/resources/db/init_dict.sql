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
