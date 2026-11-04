package com.zhiyu.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码申请入参，对应 {@code POST /api/auth/password/forgot}。
 */
@Data
public class PasswordForgotDTO {

    /** 登录名（学号或昵称） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;
}
