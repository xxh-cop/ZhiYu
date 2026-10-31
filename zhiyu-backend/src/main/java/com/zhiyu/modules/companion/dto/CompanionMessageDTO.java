package com.zhiyu.modules.companion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanionMessageDTO {

    @NotBlank(message = "请先说说此刻的想法")
    @Size(max = 500, message = "一次最多输入 500 字")
    private String content;
}
