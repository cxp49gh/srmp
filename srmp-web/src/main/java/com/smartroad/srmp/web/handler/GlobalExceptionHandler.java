package com.smartroad.srmp.web.handler;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.common.exception.BizException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
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
