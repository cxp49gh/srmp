INSERT INTO sys_org(tenant_id, org_code, org_name, org_type)
VALUES ('default', 'ROOT', '默认组织', 'BUREAU')
ON CONFLICT (tenant_id, org_code) DO NOTHING;

INSERT INTO sys_user(tenant_id, username, password, real_name, status)
VALUES ('default', 'admin', 'admin123', '系统管理员', 'ENABLED')
ON CONFLICT (tenant_id, username) DO NOTHING;
