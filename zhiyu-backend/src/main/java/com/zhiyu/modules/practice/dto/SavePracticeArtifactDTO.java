package com.zhiyu.modules.practice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SavePracticeArtifactDTO {

    private Boolean consent;

    @Size(max = 100, message = "结构说明不得超过 100 字")
    private String structureLabel;

    @Size(max = 500, message = "备注不得超过 500 字")
    private String notes;

    private List<String> palette;
}
