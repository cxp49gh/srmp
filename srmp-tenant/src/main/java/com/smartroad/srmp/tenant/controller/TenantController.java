package com.smartroad.srmp.tenant.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    @GetMapping("/current")
    public R<Map<String, String>> current() {
        return R.ok(Collections.singletonMap("tenantId", TenantContextHolder.getTenantId()));
    }
}
