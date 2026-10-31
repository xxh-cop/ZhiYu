package com.zhiyu.modules.practice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePracticeSessionDTO {

    @NotBlank(message = "练习类型不能为空")
    @Size(max = 20, message = "练习类型过长")
    private String kind;

    @Size(max = 32, message = "情绪代码过长")
    private String moodCode;

    @NotNull(message = "请选择可用时间")
    @Min(value = 1, message = "练习时间至少 1 分钟")
    @Max(value = 180, message = "练习时间最多 180 分钟")
    private Integer minutesBudget;

    @Size(max = 32, message = "纹样 ID 过长")
    private String heritagePatternId;
}
