package com.zhiyu.modules.practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PracticeSessionActionDTO {

    @NotBlank(message = "操作不能为空")
    @Pattern(
            regexp = "START_LISTENING|RECOMMEND|START|PAUSE|RESUME|COMPLETE|SAVE_ARTIFACT|ABANDON",
            message = "不支持的练习操作")
    private String action;
}
