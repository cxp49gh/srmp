package com.smartroad.srmp.security.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String tenantId;
    private String username;
    private String password;
}
