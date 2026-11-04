package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作品状态变更请求体（P1-B1）。
 *
 * <p>1 发布 / 2 下架。
 * 2026-11-04（I1）：下架/驳回必须携带 {@code reason}（服务层校验，缺失 1001），
 * 落库到 {@code work.audit_reason} 并随通知正文送达作者；发布时 reason 可空。
 */
@Data
public class WorkStatusDTO {

    @NotNull(message = "status 不能为空")
    private Integer status;

    /** 审核理由；status=2（下架/驳回）时必填，status=1 可空。 */
    @Size(max = 255, message = "审核理由不能超过 255 字")
    private String reason;
}
