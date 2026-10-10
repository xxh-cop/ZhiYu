package com.zhiyu.modules.inheritor.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 传承人自更新资料请求 DTO（批次 6-A）。
 *
 * <p>所有字段可选，null 表示不更新。
 * <br>avatarUrl 非空时同步更新 user.avatar_url。
 */
@Data
public class InheritorProfileSelfUpdateDTO {

    @Size(max = 100, message = "擅长技艺长度不能超过 100")
    private String skill;

    @Size(max = 500, message = "个人简介长度不能超过 500")
    private String intro;

    @Size(max = 255, message = "荣誉长度不能超过 255")
    private String honor;

    @Size(max = 255, message = "头像 URL 过长")
    private String avatarUrl;
}
