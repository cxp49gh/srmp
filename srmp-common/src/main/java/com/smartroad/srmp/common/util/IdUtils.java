package com.smartroad.srmp.common.util;

import java.util.UUID;

public class IdUtils {
    private IdUtils() {}

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
