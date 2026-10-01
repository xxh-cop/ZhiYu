package com.zhiyu.common.exception;

import com.zhiyu.common.result.Result;
import com.zhiyu.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一收敛成 {@link Result}，并按 AGENTS.md §3 给出对应 HTTP 状态。
 *
 * <p>错误码取值见 {@code AGENTS.md} §3，不新造、不改数值。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：用异常自带的 code 反查 HTTP 状态，再原样返回 code/message。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        ResultCode matched = ResultCode.fromCode(e.getCode());
        // code 不在 AGENTS.md §3 的表里时兜底为 200
        HttpStatus status = (matched == null) ? HttpStatus.OK : matched.getHttpStatus();
        log.warn("业务异常 code={}, httpStatus={}, message={}", e.getCode(), status.value(), e.getMessage());
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 参数校验失败：取第一个字段错误的提示，固定 HTTP 400。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError == null)
                ? ResultCode.PARAM_ERROR.getMessage()
                : fieldError.getDefaultMessage();
        log.warn("参数校验失败 field={}, message={}",
                fieldError == null ? "-" : fieldError.getField(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR.getCode(), message));
    }

    /** 兜底：记录完整堆栈，只向调用方暴露通用提示，固定 HTTP 500，不泄漏内部细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ResultCode.SYSTEM_ERROR));
    }
}
