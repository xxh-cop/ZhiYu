package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员创建用户请求体（批次 4）。
 *
 * <p>支持创建学生(0)/传承人(1)/咨询师(2)/管理员(3)。
 * role=1（传承人）时可附带 skill / honor 字段，将自动创建 inheritor_profile。
 */
@Data
public class UserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @NotNull(message = "角色不能为空")
    @Min(value = 0, message = "角色码最小为 0")
    @Max(value = 3, message = "角色码最大为 3")
    private Integer role;

    @Size(max = 100, message = "学校名称过长")
    private String school;

    @Size(max = 500, message = "个人简介过长")
    private String intro;

    @Size(max = 255, message = "头像 URL 过长")
    private String avatarUrl;

    // ── 传承人专用字段（role=1 时可选）──

    @Size(max = 100, message = "擅长技艺过长")
    private String skill;

    @Size(max = 255, message = "荣誉过长")
    private String honor;
}
