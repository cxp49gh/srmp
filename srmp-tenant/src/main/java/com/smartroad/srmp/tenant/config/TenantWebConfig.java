package com.smartroad.srmp.tenant.config;

import com.smartroad.srmp.tenant.interceptor.TenantInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantWebConfig implements WebMvcConfigurer {

    @Value("${srmp.tenant.header-name:X-Tenant-Id}")
    private String tenantHeaderName;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor(tenantHeaderName))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/login");
    }
}
