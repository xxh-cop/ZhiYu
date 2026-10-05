package com.zhiyu.modules.inheritor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 传承人创建材料请求体（M2 批 5A）。
 */
@Data
public class MaterialCreateDTO {

    @NotBlank(message = "name 不能为空")
    private String name;

    private String qty;
}
