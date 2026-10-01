package com.zhiyu.common.exception;

import com.zhiyu.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带 {@link ResultCode} 的错误码与提示。
 *
 * <p>由 {@link GlobalExceptionHandler} 统一捕获并转成 {@code Result.error(code, message)}。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    /** 用标准错误码构造，提示文案取自 {@link ResultCode}。 */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 用自定义错误码与提示构造（错误码仍须是 AGENTS.md §3 中已有的值）。 */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
