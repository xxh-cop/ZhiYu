package com.zhiyu.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 全局错误码。
 *
 * <p>取值严格对应 {@code AGENTS.md} §3，**全局唯一，不得新增、不得改数值**。
 * 新增错误码必须先改 AGENTS.md §3 与 {@code openapi/openapi.yaml}。
 *
 * <p>{@code httpStatus} 是该错误码对外应返回的 HTTP 状态，同样以 AGENTS.md §3 的表为准：
 * 只有参数、鉴权、权限与系统级失败才用非 200，业务失败一律 200。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "操作成功", HttpStatus.OK),

    /** 参数校验失败 */
    PARAM_ERROR(1001, "参数校验失败", HttpStatus.BAD_REQUEST),

    /** 未登录 / token 失效 */
    UNAUTHORIZED(1002, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),

    /** 无权限 */
    FORBIDDEN(1003, "无访问权限", HttpStatus.FORBIDDEN),

    /** 用户名或密码错误（业务失败，HTTP 200） */
    BAD_CREDENTIAL(2001, "用户名或密码错误", HttpStatus.OK),

    /** 用户名已存在（业务失败，HTTP 200） */
    USER_EXISTS(2002, "用户名已存在", HttpStatus.OK),

    /** 非法状态流转（业务失败，HTTP 200） */
    BAD_STATE(3001, "非法状态流转", HttpStatus.OK),

    /**
     * AI 服务不可用（已降级，业务失败，HTTP 200）。
     *
     * <p>注意：`4001` 仅用于**非 SSE 接口**（推荐、寄语等）。SSE 接口
     * （`POST /api/emotion/chat`）的 AI 故障不使用本错误码，
     * 改用 `event: error` 推送，前端降级为缓存图文。
     */
    AI_SERVICE_ERROR(4001, "智能服务暂时不可用", HttpStatus.OK),

    /** 服务器内部错误 */
    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    /**
     * 按错误码反查枚举项。
     *
     * <p>用于把 {@link com.zhiyu.common.exception.BusinessException} 里携带的 code
     * 映射回 HTTP 状态；**查不到返回 {@code null}**，由调用方决定兜底策略。
     */
    public static ResultCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ResultCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
