package com.zhiyu.modules.craft.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 推进会话状态请求 DTO（T1.6）。
 */
@Data
public class SessionStatusDTO {

    @NotBlank(message = "操作不能为空")
    private String action;
}
