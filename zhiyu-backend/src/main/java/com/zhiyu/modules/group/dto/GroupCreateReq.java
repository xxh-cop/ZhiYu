package com.zhiyu.modules.group.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建小组请求 DTO（T1.8b）。
 */
@Data
public class GroupCreateReq {

    @NotBlank(message = "小组名称不能为空")
    @Size(max = 100, message = "小组名称不能超过 100 字")
    private String name;

    @Size(max = 255, message = "小组描述不能超过 255 字")
    private String description;

    @Min(value = 1, message = "人数上限至少为 1")
    @Max(value = 500, message = "人数上限不能超过 500")
    private Integer maxCount;
}
