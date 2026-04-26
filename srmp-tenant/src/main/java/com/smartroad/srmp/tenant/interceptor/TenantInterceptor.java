package com.smartroad.srmp.tenant.interceptor;

import com.smartroad.srmp.common.constants.TenantConstants;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TenantInterceptor implements HandlerInterceptor {

    private final String headerName;

    public TenantInterceptor() {
        this(TenantConstants.TENANT_HEADER);
    }

    public TenantInterceptor(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader(headerName);
        TenantContextHolder.setTenantId(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
    }
}
