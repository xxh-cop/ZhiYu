package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 小组状态变更请求体（P1-B1）。
 *
 * <p>0 关闭 / 1 开放。
 */
@Data
public class GroupStatusDTO {

    @NotNull(message = "status 不能为空")
    private Integer status;
}
