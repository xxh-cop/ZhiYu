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

    /**
     * 软档标记（2026-11-07 处置链）：true 仅允许随 status=1（通过）使用——
     * 审核员基于 AI 建议 SOFT_CONCERN 选择「通过并标记软档」，服务端落台账+抽样+降权+通知。
     */
    private Boolean softTier;

    /** 管理端回显的 AI 建议 narrative 值（软档窄门留证，可空）。 */
    @Size(max = 20, message = "narrative 长度非法")
    private String narrative;

    /** 管理端回显的 AI 建议 riskPoints 是否非空（抽样全覆盖层信号，可空）。 */
    private Boolean riskPointsNonEmpty;
}
