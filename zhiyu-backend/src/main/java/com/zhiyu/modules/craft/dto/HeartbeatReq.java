package com.zhiyu.modules.craft.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 心跳请求 DTO（T1.7）。
 *
 * <p>对应 OpenAPI schema {@code HeartbeatReq}。
 */
@Data
public class HeartbeatReq {

    @NotNull(message = "步骤序号不能为空")
    private Integer stepNo;

    @NotNull(message = "停留秒数不能为空")
    private Integer dwellSeconds;

    /** 是否主动退出（可选，默认 false） */
    private Boolean isExited;

    /** 是否响应了呼吸提醒（可选，默认 false） */
    private Boolean isBreathingResponded;
}
