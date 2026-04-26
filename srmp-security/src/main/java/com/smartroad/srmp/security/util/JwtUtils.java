package com.smartroad.srmp.security.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtUtils {
    private JwtUtils() {}

    /**
     * 阶段一占位实现：后续替换为标准 JWT 签名。
     */
    public static String mockToken(String tenantId, String username) {
        String raw = tenantId + ":" + username + ":" + System.currentTimeMillis();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
