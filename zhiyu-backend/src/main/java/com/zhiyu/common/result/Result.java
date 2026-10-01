package com.zhiyu.common.result;

import lombok.Getter;

/**
 * 统一响应体：{@code { code, message, data }}（见 {@code AGENTS.md} §3）。
 *
 * <p>字段私有且不可变，只读不写；对外只暴露 {@code success} / {@code error} 静态工厂。
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> {

    private final Integer code;
    private final String message;
    private final T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无数据。 */
    public static Result<Void> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带数据。 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败，错误码与提示取自 {@link ResultCode}。 */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，错误码与提示由调用方给定（错误码仍须是 AGENTS.md §3 中已有的值）。 */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
