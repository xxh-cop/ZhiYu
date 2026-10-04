package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 审核请求体（M2 批 4）。
 *
 * <p>action 取值：approve（通过）/ reject（驳回）。
 * reject 时 reason 必填。
 */
@Data
public class AuditRequest {

    /** 审核动作：approve / reject */
    @NotBlank(message = "action 不能为空")
    @Pattern(regexp = "approve|reject", message = "action 只能为 approve 或 reject")
    private String action;

    /** 驳回理由（reject 时必填，approve 时可空） */
    private String reason;
}
