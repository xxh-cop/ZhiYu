package com.zhiyu.modules.inheritor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 传承人创建/编辑步骤请求体（M2 批 5A）。
 */
@Data
public class StepCreateDTO {

    @NotNull(message = "stepNo 不能为空")
    private Integer stepNo;

    @NotBlank(message = "title 不能为空")
    private String title;

    private String guide;

    private String audioUrl;
}
