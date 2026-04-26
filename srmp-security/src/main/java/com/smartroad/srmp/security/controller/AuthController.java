package com.smartroad.srmp.security.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.security.dto.LoginRequest;
import com.smartroad.srmp.security.dto.LoginResponse;
import com.smartroad.srmp.security.util.JwtUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = new LoginResponse();
        response.setTenantId(request.getTenantId() == null ? "default" : request.getTenantId());
        response.setUsername(request.getUsername());
        response.setRealName("系统管理员");
        response.setToken(JwtUtils.mockToken(response.getTenantId(), response.getUsername()));
        return R.ok(response);
    }
}
