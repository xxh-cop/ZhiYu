package com.zhiyu.modules.companion.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CompanionMemorySaveDTO {

    private Boolean consent;

    private Map<String, String> preferences;
}
