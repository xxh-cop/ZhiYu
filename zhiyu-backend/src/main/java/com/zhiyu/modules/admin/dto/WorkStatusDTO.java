package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作品状态变更请求体（P1-B1）。
 *
 * <p>1 发布 / 2 下架。
 */
@Data
public class WorkStatusDTO {

    @NotNull(message = "status 不能为空")
    private Integer status;
}
