package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 传承人认证请求体（P1-B1）。
 *
 * <p>0 取消认证 / 1 认证通过。
 */
@Data
public class InheritorVerifyDTO {

    @NotNull(message = "verified 不能为空")
    private Integer verified;
}
