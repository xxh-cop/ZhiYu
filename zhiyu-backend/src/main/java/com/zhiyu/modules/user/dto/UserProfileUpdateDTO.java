package com.zhiyu.modules.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求 DTO（批次 4.5）。
 *
 * <p>所有字段可选，null 表示不更新。
 */
@Data
public class UserProfileUpdateDTO {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Size(max = 255, message = "头像 URL 过长")
    private String avatarUrl;

    @Size(max = 100, message = "学校名称过长")
    private String school;

    @Size(max = 500, message = "个人简介过长")
    private String intro;
}
