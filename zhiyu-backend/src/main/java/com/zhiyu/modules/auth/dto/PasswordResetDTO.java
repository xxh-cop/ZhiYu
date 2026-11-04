package com.zhiyu.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码重置入参，对应 {@code POST /api/auth/password/reset}。
 */
@Data
public class PasswordResetDTO {

    /** 一次性重置令牌（明文，只在本请求内存中出现，服务端只存其哈希） */
    @NotBlank(message = "重置令牌不能为空")
    @Size(max = 128, message = "重置令牌格式不正确")
    private String token;

    /** 新密码（与注册同一套长度约束） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String newPassword;
}
