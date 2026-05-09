package com.smartroad.srmp.web.handler;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.common.exception.BizException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException e) {
        if (e.getDetails() == null || e.getDetails().isEmpty()) {
            return R.fail(e.getCode(), e.getMessage());
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("details", e.getDetails());
        R<Map<String, Object>> r = new R<>();
        r.setCode(e.getCode());
        r.setMessage(e.getMessage());
        r.setData(data);
        return r;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().isEmpty()
                ? "参数校验失败"
                : e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return R.fail(400, message);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        return R.fail("系统异常：" + e.getMessage());
    }
}
