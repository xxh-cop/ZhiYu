package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态切换请求体（P1-A1）。
 *
 * <p>0 禁用 / 1 正常。
 */
@Data
public class UserStatusDTO {

    /** 0 禁用 / 1 正常 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
