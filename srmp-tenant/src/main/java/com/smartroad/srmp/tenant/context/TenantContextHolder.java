package com.smartroad.srmp.tenant.context;

import com.smartroad.srmp.common.constants.TenantConstants;

public class TenantContextHolder {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void setTenantId(String tenantId) {
        CONTEXT.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = CONTEXT.get();
        return tenantId == null || tenantId.trim().isEmpty()
                ? TenantConstants.DEFAULT_TENANT_ID
                : tenantId;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
