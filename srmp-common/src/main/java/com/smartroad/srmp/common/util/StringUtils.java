package com.smartroad.srmp.common.util;

public class StringUtils {
    private StringUtils() {}
    public static boolean hasText(String text) { return text != null && text.trim().length() > 0; }
}
