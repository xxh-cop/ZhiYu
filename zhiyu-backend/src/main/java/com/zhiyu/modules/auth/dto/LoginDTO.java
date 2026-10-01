package com.zhiyu.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参，对应 {@code POST /api/auth/login} 的 requestBody。
 */
@Data
public class LoginDTO {

    /** 登录名（学号或昵称） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 明文密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
