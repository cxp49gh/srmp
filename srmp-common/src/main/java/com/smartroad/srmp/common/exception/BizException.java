package com.smartroad.srmp.common.exception;

import java.util.Collections;
import java.util.List;

public class BizException extends RuntimeException {
    private final Integer code;
    private final List<String> details;

    public BizException(String message) {
        super(message);
        this.code = 500;
        this.details = Collections.emptyList();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
        this.details = Collections.emptyList();
    }

    /**
     * @param details 结构化错误列表（如 Excel 行级原因），供前端展示；可为 null
     */
    public BizException(String message, List<String> details) {
        super(message);
        this.code = 500;
        this.details = details == null ? Collections.emptyList() : details;
    }

    public Integer getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
