package com.smartroad.srmp.security.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String tenantId;
    private String username;
    private String realName;
}
