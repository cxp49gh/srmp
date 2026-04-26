package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.common.exception.BizException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public abstract class AbstractImportHandler implements ImportHandler {
    protected String s(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }
    protected String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String v = s(row, key);
            if (v != null) return v;
        }
        return null;
    }
    protected BigDecimal decimal(Map<String, String> row, String key) {
        String v = s(row, key);
        if (v == null) return null;
        try { return new BigDecimal(v); } catch (Exception e) { throw new BizException("字段" + key + "不是合法数字：" + v); }
    }
    protected Integer integer(Map<String, String> row, String key) {
        String v = s(row, key);
        if (v == null) return null;
        try { return Integer.valueOf(v); } catch (Exception e) { throw new BizException("字段" + key + "不是合法整数：" + v); }
    }
    protected LocalDateTime dateTime(Map<String, String> row, String key) {
        String v = s(row, key);
        if (v == null) return null;
        try { return LocalDateTime.parse(v); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ignored) {}
        throw new BizException("字段" + key + "不是合法时间：" + v);
    }
    protected void required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new BizException(name + "不能为空");
    }
}