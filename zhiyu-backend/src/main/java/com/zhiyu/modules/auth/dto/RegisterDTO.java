package com.zhiyu.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册入参，对应 {@code POST /api/auth/register} 的 requestBody。
 *
 * <p>校验注解由 {@code @Valid} 触发，失败时由
 * {@code com.zhiyu.common.exception.GlobalExceptionHandler} 统一收敛成 1001 / HTTP 400。
 */
@Data
public class RegisterDTO {

    /** 登录名（学号或昵称） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    /** 明文密码；仅在服务端内存中出现，落库前加密（T0.2 接数据库时再加） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    /** 展示用昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /** 学校名称，可空 */
    @Size(max = 100, message = "学校名称过长")
    private String school;
}
