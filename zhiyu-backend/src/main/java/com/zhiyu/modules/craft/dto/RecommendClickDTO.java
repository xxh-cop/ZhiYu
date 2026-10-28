package com.zhiyu.modules.craft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 推荐点击请求（20261028）。
 *
 * <p>ID 使用字符串接收：雪花 ID 在浏览器 JS 中会丢失精度。
 */
@Data
public class RecommendClickDTO {

    @NotBlank(message = "推荐编号不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "推荐编号格式不正确")
    private String recommendationId;

    @NotBlank(message = "项目编号不能为空")
    @Pattern(regexp = "^[0-9]+$", message = "项目编号格式不正确")
    private String projectId;

    @NotBlank(message = "来源不能为空")
    @Pattern(regexp = "HOME|RECOMMEND", message = "来源只能是 HOME 或 RECOMMEND")
    private String source;
}
